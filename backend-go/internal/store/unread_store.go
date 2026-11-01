package store

import (
	"context"
	"fmt"
	"sync"

	"github.com/redis/go-redis/v9"
)

// UnreadStore 管理"每个用户在每个会话里的未读数"。
//
// 为什么不用 MySQL 的 COUNT(*)：
//   - 会话列表页要给每个会话显示未读数，N 个会话就是 N 条 COUNT，直接 N+1 + 全表扫；
//   - 未读数是高频写（每条消息每人 +1）高频读（每次打开列表），
//     属于典型的 Redis 计数器场景。
//
// 语义约定：
//   - 发送者自己永远不产生未读；
//   - 用户打开会话 / 主动已读时 Reset；
//   - 读失败时降级为 0（列表页不能因为 Redis 抖动整个 500）。
type UnreadStore interface {
	// Increment 给单个用户的会话未读 +1，返回增加后的值。
	Increment(conversationID, userID uint64) (int64, error)
	// IncrementForMembers 给除 senderID 外的所有成员 +1（一条消息只该产生 N-1 个未读）。
	IncrementForMembers(conversationID uint64, memberIDs []uint64, senderID uint64) error
	// Get 读取单个用户的会话未读。
	Get(conversationID, userID uint64) int64
	// GetBatch 一次取某用户多个会话的未读 —— 会话列表页专用，避免 N 次往返。
	GetBatch(userID uint64, conversationIDs []uint64) map[uint64]int64
	// Reset 清零（用户进入会话/点已读时调用）。
	Reset(conversationID, userID uint64) error
}

// InMemoryUnreadStore 是阶段 1/降级用的内存实现。
//
// 结构：userID -> conversationID -> count。
// 为什么不是 conversationID -> userID：业务查询几乎都是"给我这个人的所有未读"，
// 反过来的结构会让会话列表页退化成全表遍历。
type InMemoryUnreadStore struct {
	mu   sync.RWMutex
	data map[uint64]map[uint64]int64
}

// NewInMemoryUnreadStore 创建内存未读计数。
func NewInMemoryUnreadStore() *InMemoryUnreadStore {
	return &InMemoryUnreadStore{data: make(map[uint64]map[uint64]int64)}
}

var _ UnreadStore = (*InMemoryUnreadStore)(nil)

// Increment 未读 +1。
func (s *InMemoryUnreadStore) Increment(conversationID, userID uint64) (int64, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	m, ok := s.data[userID]
	if !ok {
		m = make(map[uint64]int64)
		s.data[userID] = m
	}
	m[conversationID]++
	return m[conversationID], nil
}

// IncrementForMembers 批量 +1（跳过发送者）。
func (s *InMemoryUnreadStore) IncrementForMembers(conversationID uint64, memberIDs []uint64, senderID uint64) error {
	for _, uid := range dedupIDs(memberIDs) {
		if uid == senderID {
			continue
		}
		if _, err := s.Increment(conversationID, uid); err != nil {
			return err
		}
	}
	return nil
}

// Get 读取未读。
func (s *InMemoryUnreadStore) Get(conversationID, userID uint64) int64 {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.data[userID][conversationID]
}

// GetBatch 批量读取。
func (s *InMemoryUnreadStore) GetBatch(userID uint64, conversationIDs []uint64) map[uint64]int64 {
	s.mu.RLock()
	defer s.mu.RUnlock()

	out := make(map[uint64]int64, len(conversationIDs))
	for _, cid := range conversationIDs {
		out[cid] = s.data[userID][cid]
	}
	return out
}

// Reset 清零。
func (s *InMemoryUnreadStore) Reset(conversationID, userID uint64) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if m, ok := s.data[userID]; ok {
		delete(m, conversationID)
	}
	return nil
}

// RedisUnreadStore 是 UnreadStore 的 Redis 实现。
//
// key: unread:<userID> 是一个 hash，field = conversationID，value = 未读数。
//
// 为什么用 hash 而不是 unread:<conversationID>:<userID> 这种独立 key：
//   - 会话列表页要一次拿"我这个人的所有会话未读"，HGETALL 一次往返；
//     独立 key 就得 MGET 且还要自己拼 N 个 key（N 次网络包）；
//   - 已读清零是 HDEL 一个 field，不用扫 key。
//
// 代价：单个 hash 会随会话数增长。这里用 30 天 TTL 兜底回收，
// 且用户量级下"一个人的会话数"天然有上界（几百个）。
type RedisUnreadStore struct {
	client *redis.Client
}

// NewRedisUnreadStore 创建 Redis 未读计数。
func NewRedisUnreadStore(client *redis.Client) *RedisUnreadStore {
	return &RedisUnreadStore{client: client}
}

var _ UnreadStore = (*RedisUnreadStore)(nil)

// Increment 未读 +1，并顺手续期（活跃用户不该被 30 天 TTL 清掉未读）。
func (s *RedisUnreadStore) Increment(conversationID, userID uint64) (int64, error) {
	ctx := context.Background()
	key := unreadKey(userID)

	pipe := s.client.Pipeline()
	incr := pipe.HIncrBy(ctx, key, fmt.Sprint(conversationID), 1)
	pipe.Expire(ctx, key, unreadTTL)
	if _, err := pipe.Exec(ctx); err != nil {
		return 0, err
	}
	return incr.Val(), nil
}

// IncrementForMembers 用 pipeline 把 N-1 次 HINCRBY 压成一次往返。
//
// 注意这里没用事务：未读计数是"允许少量误差"的统计值，
// 为它上 MULTI 会放大锁竞争，收益远小于成本。
func (s *RedisUnreadStore) IncrementForMembers(conversationID uint64, memberIDs []uint64, senderID uint64) error {
	ctx := context.Background()
	field := fmt.Sprint(conversationID)

	pipe := s.client.Pipeline()
	written := 0
	for _, uid := range dedupIDs(memberIDs) {
		if uid == senderID {
			continue
		}
		key := unreadKey(uid)
		pipe.HIncrBy(ctx, key, field, 1)
		pipe.Expire(ctx, key, unreadTTL)
		written++
	}
	if written == 0 {
		return nil
	}
	_, err := pipe.Exec(ctx)
	return err
}

// Get 读取单个未读。
func (s *RedisUnreadStore) Get(conversationID, userID uint64) int64 {
	ctx := context.Background()
	n, err := s.client.HGet(ctx, unreadKey(userID), fmt.Sprint(conversationID)).Int64()
	if err != nil { // redis.Nil（没这个 field）或 Redis 故障，统一按 0
		return 0
	}
	return n
}

// GetBatch 一次 HGETALL，把该用户所有会话的未读拿回来。
//
// 只返回请求里出现的 conversationID（缺省 0），避免把 hash 里的历史脏 field 带给前端。
func (s *RedisUnreadStore) GetBatch(userID uint64, conversationIDs []uint64) map[uint64]int64 {
	out := make(map[uint64]int64, len(conversationIDs))
	for _, cid := range conversationIDs {
		out[cid] = 0
	}

	ctx := context.Background()
	raw, err := s.client.HGetAll(ctx, unreadKey(userID)).Result()
	if err != nil || len(raw) == 0 {
		return out
	}
	for field, val := range raw {
		cid, err := parseUint(field)
		if err != nil {
			continue
		}
		if _, wanted := out[cid]; !wanted {
			continue
		}
		n, err := parseInt64(val)
		if err != nil {
			continue
		}
		out[cid] = n
	}
	return out
}

// Reset 清零。
//
// 空 hash 顺手删掉：HDEL 之后 hash 仍存在的话会白占内存直到 TTL，
// 而且 HGETALL 会返回空 map（这里虽然能处理，但没必要留垃圾）。
func (s *RedisUnreadStore) Reset(conversationID, userID uint64) error {
	ctx := context.Background()
	key := unreadKey(userID)

	if err := s.client.HDel(ctx, key, fmt.Sprint(conversationID)).Err(); err != nil {
		return err
	}
	if n, err := s.client.HLen(ctx, key).Result(); err == nil && n == 0 {
		return s.client.Del(ctx, key).Err()
	}
	return nil
}

// parseUint / parseInt64 是 strconv 的薄封装，放在这里避免每个文件都 import strconv。
func parseUint(s string) (uint64, error) {
	var n uint64
	_, err := fmt.Sscan(s, &n)
	return n, err
}

func parseInt64(s string) (int64, error) {
	var n int64
	_, err := fmt.Sscan(s, &n)
	return n, err
}

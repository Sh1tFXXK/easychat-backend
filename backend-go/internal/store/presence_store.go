package store

import (
	"context"
	"strconv"
	"sync"
	"time"

	"github.com/redis/go-redis/v9"
)

// PresenceStore 管理用户在线状态。
//
// 核心思路：**在线 = 一个带 TTL 的 key 还存在**。
//
//	presence:online:<userID>    -> 1，TTL 90s，客户端心跳续期
//	presence:lastseen:<userID>  -> 最后活跃时间（Unix 秒）
//
// 为什么不用"上线 SET 1 / 下线 DEL"的布尔状态：
// 进程被 kill -9、手机进电梯断网时，服务端根本没机会执行 DEL，
// 用户会永远显示"在线"。TTL 让掉线自动发生，显式 DEL 只是让状态更及时。
type PresenceStore interface {
	// Heartbeat 续期在线状态。
	Heartbeat(userID uint64, ttl time.Duration) error
	// Offline 主动标记离线（正常关闭连接时调用），同时记录最后在线时间。
	Offline(userID uint64) error
	IsOnline(userID uint64) bool
	// BatchOnline 批量查在线状态 —— 好友列表/群成员列表一次问完，避免 N 次往返。
	BatchOnline(userIDs []uint64) map[uint64]bool
	// LastSeen 返回最后活跃时间；从未上线过返回零值。
	LastSeen(userID uint64) time.Time
}

// InMemoryPresenceStore 是内存实现（单机 / 降级用）。
//
// 注意：多实例部署时内存版各存各的，用户会被随机判在线/离线 ——
// 这就是阶段 2 必须换 Redis 的原因之一。
type InMemoryPresenceStore struct {
	mu       sync.RWMutex
	online   map[uint64]time.Time // userID -> 过期时间
	lastSeen map[uint64]time.Time
}

// NewInMemoryPresenceStore 创建内存在线状态存储。
func NewInMemoryPresenceStore() *InMemoryPresenceStore {
	return &InMemoryPresenceStore{
		online:   make(map[uint64]time.Time),
		lastSeen: make(map[uint64]time.Time),
	}
}

var _ PresenceStore = (*InMemoryPresenceStore)(nil)

// Heartbeat 续期。
func (s *InMemoryPresenceStore) Heartbeat(userID uint64, ttl time.Duration) error {
	now := time.Now()
	s.mu.Lock()
	s.online[userID] = now.Add(ttl)
	s.lastSeen[userID] = now
	s.mu.Unlock()
	return nil
}

// Offline 标记离线。
func (s *InMemoryPresenceStore) Offline(userID uint64) error {
	s.mu.Lock()
	delete(s.online, userID)
	s.lastSeen[userID] = time.Now()
	s.mu.Unlock()
	return nil
}

// IsOnline 判断在线（顺带惰性清理过期条目）。
func (s *InMemoryPresenceStore) IsOnline(userID uint64) bool {
	s.mu.RLock()
	exp, ok := s.online[userID]
	s.mu.RUnlock()
	return ok && time.Now().Before(exp)
}

// BatchOnline 批量判断。
func (s *InMemoryPresenceStore) BatchOnline(userIDs []uint64) map[uint64]bool {
	out := make(map[uint64]bool, len(userIDs))
	for _, id := range userIDs {
		out[id] = s.IsOnline(id)
	}
	return out
}

// LastSeen 最后活跃时间。
func (s *InMemoryPresenceStore) LastSeen(userID uint64) time.Time {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.lastSeen[userID]
}

// RedisPresenceStore 是 PresenceStore 的 Redis 实现。
type RedisPresenceStore struct {
	client *redis.Client
}

// NewRedisPresenceStore 创建 Redis 在线状态存储。
func NewRedisPresenceStore(client *redis.Client) *RedisPresenceStore {
	return &RedisPresenceStore{client: client}
}

var _ PresenceStore = (*RedisPresenceStore)(nil)

// Heartbeat 用 SET ... EX 续期。
//
// 不用 EXPIRE 单独续：SET 是幂等的，重复心跳不会出错；
// 而且 key 不存在时 SET 会直接重建（用户重连后第一次心跳就能恢复在线）。
func (s *RedisPresenceStore) Heartbeat(userID uint64, ttl time.Duration) error {
	ctx := context.Background()
	now := time.Now()

	pipe := s.client.Pipeline()
	pipe.Set(ctx, onlineKey(userID), 1, ttl)
	pipe.Set(ctx, lastSeenKey(userID), now.Unix(), lastSeenTTL)
	_, err := pipe.Exec(ctx)
	return err
}

// Offline 删除在线 key 并记录最后在线时间。
func (s *RedisPresenceStore) Offline(userID uint64) error {
	ctx := context.Background()

	pipe := s.client.Pipeline()
	pipe.Set(ctx, lastSeenKey(userID), time.Now().Unix(), lastSeenTTL)
	pipe.Del(ctx, onlineKey(userID))
	_, err := pipe.Exec(ctx)
	return err
}

// IsOnline 用 EXISTS 判断，key 过期即离线。
func (s *RedisPresenceStore) IsOnline(userID uint64) bool {
	ctx := context.Background()
	n, err := s.client.Exists(ctx, onlineKey(userID)).Result()
	return err == nil && n > 0
}

// BatchOnline 用 pipeline 一次发 N 个 EXISTS。
//
// 也可以写成 EXISTS k1 k2 k3（Redis 支持多 key），但那样拿不到"每个 key 分别存在与否"，
// 只能知道总数，所以这里用 pipeline 逐 key 查。
func (s *RedisPresenceStore) BatchOnline(userIDs []uint64) map[uint64]bool {
	out := make(map[uint64]bool, len(userIDs))
	if len(userIDs) == 0 {
		return out
	}

	ctx := context.Background()
	pipe := s.client.Pipeline()
	cmds := make([]*redis.IntCmd, len(userIDs))
	for i, id := range userIDs {
		cmds[i] = pipe.Exists(ctx, onlineKey(id))
	}
	_, _ = pipe.Exec(ctx) // 单个命令的错误在下面按 key 处理

	for i, id := range userIDs {
		out[id] = cmds[i].Err() == nil && cmds[i].Val() > 0
	}
	return out
}

// LastSeen 读取最后活跃时间。
func (s *RedisPresenceStore) LastSeen(userID uint64) time.Time {
	ctx := context.Background()
	raw, err := s.client.Get(ctx, lastSeenKey(userID)).Result()
	if err != nil {
		return time.Time{}
	}
	sec, err := strconv.ParseInt(raw, 10, 64)
	if err != nil {
		return time.Time{}
	}
	return time.Unix(sec, 0)
}

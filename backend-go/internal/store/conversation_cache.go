package store

import (
	"context"
	"encoding/json"
	"sync"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/redis/go-redis/v9"
)

// ConversationCache 缓存会话列表的"骨架"。
//
// 为什么缓存整个列表而不是每会话一条：
//   - 列表页一次要 N 条，逐条缓存就是 N 次往返，缓存本身成了新瓶颈；
//   - 会话列表的读远多于写（打开 App / 切 tab 都读，只有发消息才变）。
//
// 关键设计：**缓存里不存未读数**（Unread 持久化为 0）。
// 未读变化频率高（每条消息都变），塞进缓存就得每次发消息失效所有人的列表；
// 正确做法是骨架走缓存、未读实时从 Redis 计数叠加 —— 见 handler.GetChats。
type ConversationCache interface {
	// Get 命中返回副本；miss 或已过期返回 false。
	Get(userID uint64) ([]model.ChatSession, bool)
	Set(userID uint64, sessions []model.ChatSession) error
	// Invalidate 失效指定用户的缓存（发消息/改群名/成员变动时调用）。
	Invalidate(userIDs ...uint64) error
}

type chatListEntry struct {
	sessions  []model.ChatSession
	expiresAt time.Time
}

// InMemoryConversationCache 是内存实现（单机 / 降级用）。
type InMemoryConversationCache struct {
	mu      sync.RWMutex
	entries map[uint64]chatListEntry
	ttl     time.Duration
}

// NewInMemoryConversationCache 创建内存会话列表缓存。
func NewInMemoryConversationCache(ttl time.Duration) *InMemoryConversationCache {
	return &InMemoryConversationCache{
		entries: make(map[uint64]chatListEntry),
		ttl:     ttl,
	}
}

var _ ConversationCache = (*InMemoryConversationCache)(nil)

// Get 读缓存。
func (s *InMemoryConversationCache) Get(userID uint64) ([]model.ChatSession, bool) {
	s.mu.RLock()
	e, ok := s.entries[userID]
	s.mu.RUnlock()
	if !ok || time.Now().After(e.expiresAt) {
		return nil, false
	}
	return cloneSessions(e.sessions), true
}

// Set 写缓存。
func (s *InMemoryConversationCache) Set(userID uint64, sessions []model.ChatSession) error {
	s.mu.Lock()
	s.entries[userID] = chatListEntry{
		sessions:  cloneSessions(sessions),
		expiresAt: time.Now().Add(s.ttl),
	}
	s.mu.Unlock()
	return nil
}

// Invalidate 删除缓存。
func (s *InMemoryConversationCache) Invalidate(userIDs ...uint64) error {
	s.mu.Lock()
	for _, id := range userIDs {
		delete(s.entries, id)
	}
	s.mu.Unlock()
	return nil
}

// cloneSessions 复制切片，避免调用方改返回值污染缓存。
func cloneSessions(in []model.ChatSession) []model.ChatSession {
	out := make([]model.ChatSession, len(in))
	copy(out, in)
	return out
}

// RedisConversationCache 是 ConversationCache 的 Redis 实现。
//
// key: cache:chatlist:<userID> -> 整个列表的 JSON，TTL 60s。
//
// 用 JSON 而不是 hash：列表是有序整体，读的时候一次性反序列化最简单；
// hash 虽然能只更新某个会话，但会话排序会变，增量维护反而更复杂。
type RedisConversationCache struct {
	client *redis.Client
	ttl    time.Duration
}

// NewRedisConversationCache 创建 Redis 会话列表缓存。
func NewRedisConversationCache(client *redis.Client, ttl time.Duration) *RedisConversationCache {
	return &RedisConversationCache{client: client, ttl: ttl}
}

var _ ConversationCache = (*RedisConversationCache)(nil)

// Get 读缓存；反序列化失败按 miss 处理（不要让脏缓存拖垮接口）。
func (s *RedisConversationCache) Get(userID uint64) ([]model.ChatSession, bool) {
	ctx := context.Background()

	raw, err := s.client.Get(ctx, chatListKey(userID)).Bytes()
	if err != nil {
		return nil, false
	}

	var sessions []model.ChatSession
	if err := json.Unmarshal(raw, &sessions); err != nil {
		// 脏数据（模型字段改名、人工写入）：删掉，下次重建。
		_ = s.client.Del(ctx, chatListKey(userID)).Err()
		return nil, false
	}
	return sessions, true
}

// Set 写缓存。
func (s *RedisConversationCache) Set(userID uint64, sessions []model.ChatSession) error {
	ctx := context.Background()

	// 空列表也要缓存：新用户没有会话时，否则每次打开都在数据库里空查一遍。
	if sessions == nil {
		sessions = []model.ChatSession{}
	}
	raw, err := json.Marshal(sessions)
	if err != nil {
		return err
	}
	return s.client.Set(ctx, chatListKey(userID), raw, s.ttl).Err()
}

// Invalidate 批量删除。
func (s *RedisConversationCache) Invalidate(userIDs ...uint64) error {
	if len(userIDs) == 0 {
		return nil
	}
	keys := make([]string, 0, len(userIDs))
	for _, id := range userIDs {
		keys = append(keys, chatListKey(id))
	}
	return s.client.Del(context.Background(), keys...).Err()
}

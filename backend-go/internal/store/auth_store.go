package store

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"sync"
	"time"
)

// ErrTokenInvalid 表示 token 不存在、已过期或已被登出。
var ErrTokenInvalid = errors.New("invalid or expired token")

// TokenStore 管理登录会话。
//
// 为什么不用 JWT（阶段 1）：
//   - JWT 无状态，登出必须额外维护黑名单，反而需要一套存储；
//   - 内存实现天然支持"立即失效"，更适合还在快速迭代的阶段 1；
//   - 接口保持不变，阶段 2 换成 Redis（SET token userID EX ttl）时 handler 不用改。
type TokenStore interface {
	// Issue 为一个用户签发不透明 token。
	Issue(userID uint64, ttl time.Duration) (token string, expiresAt time.Time, err error)
	// Resolve 校验 token 并返回用户 ID。
	Resolve(token string) (uint64, error)
	// Revoke 使 token 立即失效（登出）。
	Revoke(token string) error
}

type sessionEntry struct {
	userID    uint64
	expiresAt time.Time
}

// InMemoryTokenStore 是线程安全的内存实现。
type InMemoryTokenStore struct {
	mu       sync.RWMutex
	sessions map[string]sessionEntry
}

// NewInMemoryTokenStore 创建内存会话存储。
func NewInMemoryTokenStore() *InMemoryTokenStore {
	return &InMemoryTokenStore{sessions: make(map[string]sessionEntry)}
}

// Issue 生成 32 字节随机 token（hex 后 64 字符）。
//
// 为什么不用自增 id / 时间戳：token 是可预测的凭据，
// 一旦可预测就等于任何人都能伪造登录态。crypto/rand 是底线。
func (s *InMemoryTokenStore) Issue(userID uint64, ttl time.Duration) (string, time.Time, error) {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		return "", time.Time{}, err
	}
	token := hex.EncodeToString(buf)
	expiresAt := time.Now().Add(ttl)

	s.mu.Lock()
	s.sessions[token] = sessionEntry{userID: userID, expiresAt: expiresAt}
	s.mu.Unlock()

	return token, expiresAt, nil
}

// Resolve 校验 token；过期条目顺手删除（惰性清理，不需要后台 goroutine）。
func (s *InMemoryTokenStore) Resolve(token string) (uint64, error) {
	s.mu.RLock()
	entry, ok := s.sessions[token]
	s.mu.RUnlock()

	if !ok {
		return 0, ErrTokenInvalid
	}
	if time.Now().After(entry.expiresAt) {
		s.mu.Lock()
		delete(s.sessions, token)
		s.mu.Unlock()
		return 0, ErrTokenInvalid
	}
	return entry.userID, nil
}

// Revoke 删除 token（幂等）。
func (s *InMemoryTokenStore) Revoke(token string) error {
	s.mu.Lock()
	delete(s.sessions, token)
	s.mu.Unlock()
	return nil
}

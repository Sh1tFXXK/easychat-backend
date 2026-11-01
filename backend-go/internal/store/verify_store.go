package store

import (
	"crypto/rand"
	"errors"
	"fmt"
	"sync"
	"time"
)

// 验证码相关错误。
var (
	ErrVerifyTooFrequent = errors.New("verification code sent too frequently")
	ErrVerifyCodeInvalid = errors.New("invalid verification code")
)

// VerifyStore 管理邮箱/手机验证码。
type VerifyStore interface {
	// Send 为目标（邮箱/手机号）生成验证码；60 秒内重复请求返回 ErrVerifyTooFrequent。
	Send(target string) (code string, ttl time.Duration, err error)
	// Verify 校验验证码；成功后立即失效（一次性，防重放）。
	Verify(target, code string) bool
}

type verifyEntry struct {
	code     string
	expires  time.Time
	lastSent time.Time
	attempts int
}

// InMemoryVerifyStore 是内存实现。
//
// 现实实现要落 Redis：SET code:{target} <code> EX 300 + SET cooldown:{target} 1 EX 60。
// 这里的字段一一对应，换实现时逻辑不变。
type InMemoryVerifyStore struct {
	mu      sync.Mutex
	entries map[string]*verifyEntry
}

// NewInMemoryVerifyStore 创建验证码存储。
func NewInMemoryVerifyStore() *InMemoryVerifyStore {
	return &InMemoryVerifyStore{entries: make(map[string]*verifyEntry)}
}

// Send 生成 6 位数字验证码，有效期 5 分钟，发送冷却 60 秒。
func (s *InMemoryVerifyStore) Send(target string) (string, time.Duration, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	now := time.Now()
	if e, ok := s.entries[target]; ok && now.Sub(e.lastSent) < 60*time.Second {
		return "", 0, ErrVerifyTooFrequent
	}

	// crypto/rand 而不是 math/rand：验证码是安全凭据，不能用可预测的伪随机。
	buf := make([]byte, 4)
	if _, err := rand.Read(buf); err != nil {
		return "", 0, err
	}
	n := (uint32(buf[0])<<24 | uint32(buf[1])<<16 | uint32(buf[2])<<8 | uint32(buf[3])) % 1000000
	code := fmt.Sprintf("%06d", n)

	ttl := 5 * time.Minute
	s.entries[target] = &verifyEntry{
		code:     code,
		expires:  now.Add(ttl),
		lastSent: now,
	}
	return code, ttl, nil
}

// Verify 校验并消费验证码。
//
// 规则：
//   - 过期 → false
//   - 错误 → attempts+1，超过 5 次直接作废（防 6 位数字被暴力枚举）
//   - 正确 → 删除条目（一次性）
func (s *InMemoryVerifyStore) Verify(target, code string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()

	e, ok := s.entries[target]
	if !ok || time.Now().After(e.expires) {
		delete(s.entries, target)
		return false
	}
	if e.code != code {
		e.attempts++
		if e.attempts >= 5 {
			delete(s.entries, target)
		}
		return false
	}
	delete(s.entries, target)
	return true
}

// Peek 仅供开发期调试（生产环境不要暴露验证码）。
func (s *InMemoryVerifyStore) Peek(target string) (string, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	e, ok := s.entries[target]
	if !ok {
		return "", false
	}
	return e.code, true
}

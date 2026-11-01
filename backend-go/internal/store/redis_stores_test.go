package store

import (
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
	"github.com/redis/go-redis/v9"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
)

// newTestRedis 起一个进程内的 miniredis（真实跑 Redis 协议，不需要装 redis-server）。
// 它能模拟 TTL/过期/事务，足够验证 key 设计与语义；
// 真实 Redis 的集群/Lua 行为在 docker-compose 里再验。
func newTestRedis(t *testing.T) (*miniredis.Miniredis, *redis.Client) {
	t.Helper()

	srv, err := miniredis.Run()
	if err != nil {
		t.Fatalf("start miniredis: %v", err)
	}
	t.Cleanup(srv.Close)

	client := redis.NewClient(&redis.Options{Addr: srv.Addr()})
	t.Cleanup(func() { _ = client.Close() })
	return srv, client
}

func TestRedisTokenStore(t *testing.T) {
	srv, client := newTestRedis(t)
	s := NewRedisTokenStore(client)

	token, expiresAt, err := s.Issue(42, time.Hour)
	if err != nil {
		t.Fatalf("issue: %v", err)
	}
	if len(token) != 64 {
		t.Fatalf("token length = %d, want 64", len(token))
	}
	if time.Until(expiresAt) <= 0 {
		t.Fatalf("expiresAt should be in the future, got %v", expiresAt)
	}

	uid, err := s.Resolve(token)
	if err != nil || uid != 42 {
		t.Fatalf("resolve = (%d, %v), want (42, nil)", uid, err)
	}

	// 过期后必须失效（模拟 24 小时）。
	srv.FastForward(2 * time.Hour)
	if _, err := s.Resolve(token); err != ErrTokenInvalid {
		t.Fatalf("expired token resolve err = %v, want ErrTokenInvalid", err)
	}

	// 重新签发再登出，必须立即失效。
	token2, _, _ := s.Issue(42, time.Hour)
	if err := s.Revoke(token2); err != nil {
		t.Fatalf("revoke: %v", err)
	}
	if _, err := s.Resolve(token2); err != ErrTokenInvalid {
		t.Fatalf("revoked token still valid, err = %v", err)
	}
}

func TestRedisVerifyStore(t *testing.T) {
	_, client := newTestRedis(t)
	s := NewRedisVerifyStore(client)

	code, ttl, err := s.Send("a@b.com")
	if err != nil {
		t.Fatalf("send: %v", err)
	}
	if len(code) != 6 {
		t.Fatalf("code = %q, want 6 digits", code)
	}
	if ttl != verifyCodeTTL {
		t.Fatalf("ttl = %v, want %v", ttl, verifyCodeTTL)
	}

	// 60 秒冷却：第二次发送必须被拒。
	if _, _, err := s.Send("a@b.com"); err != ErrVerifyTooFrequent {
		t.Fatalf("second send err = %v, want ErrVerifyTooFrequent", err)
	}

	// 错误验证码不通过，且会被计数。
	if s.Verify("a@b.com", "000000") {
		t.Fatal("wrong code accepted")
	}

	// 正确验证码通过，且一次性。
	if !s.Verify("a@b.com", code) {
		t.Fatal("correct code rejected")
	}
	if s.Verify("a@b.com", code) {
		t.Fatal("code accepted twice (replay)")
	}
}

func TestRedisVerifyStore_LockAfterFiveAttempts(t *testing.T) {
	_, client := newTestRedis(t)
	s := NewRedisVerifyStore(client)

	code, _, err := s.Send("a@b.com")
	if err != nil {
		t.Fatalf("send: %v", err)
	}

	wrong := "000000"
	if code == wrong {
		wrong = "111111"
	}
	for i := 0; i < verifyMaxAttempts; i++ {
		if s.Verify("a@b.com", wrong) {
			t.Fatalf("wrong code accepted at attempt %d", i+1)
		}
	}

	// 5 次错误后验证码作废：即使拿正确的来也不行。
	if s.Verify("a@b.com", code) {
		t.Fatal("code still valid after max attempts")
	}
}

func TestRedisUnreadStore(t *testing.T) {
	_, client := newTestRedis(t)
	s := NewRedisUnreadStore(client)

	members := []uint64{1, 2, 3}

	// 用户 1 发消息：2、3 各 +1，1 自己不加。
	if err := s.IncrementForMembers(10, members, 1); err != nil {
		t.Fatalf("increment: %v", err)
	}
	if got := s.Get(10, 2); got != 1 {
		t.Fatalf("user 2 unread = %d, want 1", got)
	}
	if got := s.Get(10, 1); got != 0 {
		t.Fatalf("sender unread = %d, want 0", got)
	}

	// 再来两条。
	_ = s.IncrementForMembers(10, members, 1)
	_ = s.IncrementForMembers(10, members, 1)
	if got := s.Get(10, 3); got != 3 {
		t.Fatalf("user 3 unread = %d, want 3", got)
	}

	// 批量读：只返回请求的会话，缺省 0。
	batch := s.GetBatch(2, []uint64{10, 11})
	if batch[10] != 3 || batch[11] != 0 {
		t.Fatalf("batch = %v, want {10:3, 11:0}", batch)
	}

	// 已读清零。
	if err := s.Reset(10, 2); err != nil {
		t.Fatalf("reset: %v", err)
	}
	if got := s.Get(10, 2); got != 0 {
		t.Fatalf("after reset unread = %d, want 0", got)
	}
	// 别人不受影响。
	if got := s.Get(10, 3); got != 3 {
		t.Fatalf("user 3 should be unaffected, got %d", got)
	}
}

func TestRedisPresenceStore(t *testing.T) {
	srv, client := newTestRedis(t)
	s := NewRedisPresenceStore(client)

	if err := s.Heartbeat(7, OnlineTTL); err != nil {
		t.Fatalf("heartbeat: %v", err)
	}
	if !s.IsOnline(7) {
		t.Fatal("user 7 should be online")
	}
	if s.IsOnline(8) {
		t.Fatal("user 8 should be offline")
	}

	batch := s.BatchOnline([]uint64{7, 8})
	if !batch[7] || batch[8] {
		t.Fatalf("batch = %v, want {7:true, 8:false}", batch)
	}

	// 心跳过期 = 掉线（模拟进程崩溃，没人调 Offline）。
	srv.FastForward(OnlineTTL + time.Second)
	if s.IsOnline(7) {
		t.Fatal("user 7 should expire to offline")
	}
	if s.LastSeen(7).IsZero() {
		t.Fatal("last seen should be recorded")
	}

	// 主动下线立即生效。
	_ = s.Heartbeat(9, OnlineTTL)
	if err := s.Offline(9); err != nil {
		t.Fatalf("offline: %v", err)
	}
	if s.IsOnline(9) {
		t.Fatal("user 9 should be offline after Offline()")
	}
}

func TestRedisConversationCache(t *testing.T) {
	srv, client := newTestRedis(t)
	s := NewRedisConversationCache(client, time.Minute)

	sessions := []model.ChatSession{
		{ConversationID: 1, Title: "alice"},
		{ConversationID: 2, Title: "golang"},
	}
	if err := s.Set(100, sessions); err != nil {
		t.Fatalf("set: %v", err)
	}

	got, ok := s.Get(100)
	if !ok || len(got) != 2 || got[1].Title != "golang" {
		t.Fatalf("get = (%v, %v)", got, ok)
	}

	// 失效后必须 miss。
	if err := s.Invalidate(100); err != nil {
		t.Fatalf("invalidate: %v", err)
	}
	if _, ok := s.Get(100); ok {
		t.Fatal("cache should miss after invalidate")
	}

	// TTL 兜底过期。
	_ = s.Set(101, sessions)
	srv.FastForward(2 * time.Minute)
	if _, ok := s.Get(101); ok {
		t.Fatal("cache should expire after ttl")
	}
}

func TestInMemoryUnreadAndPresence(t *testing.T) {
	u := NewInMemoryUnreadStore()
	if err := u.IncrementForMembers(1, []uint64{1, 2}, 1); err != nil {
		t.Fatalf("increment: %v", err)
	}
	if u.Get(1, 2) != 1 || u.Get(1, 1) != 0 {
		t.Fatalf("memory unread wrong: %d %d", u.Get(1, 2), u.Get(1, 1))
	}
	_ = u.Reset(1, 2)
	if u.Get(1, 2) != 0 {
		t.Fatal("memory unread reset failed")
	}

	p := NewInMemoryPresenceStore()
	_ = p.Heartbeat(1, 50*time.Millisecond)
	if !p.IsOnline(1) {
		t.Fatal("memory presence should be online")
	}
	time.Sleep(60 * time.Millisecond)
	if p.IsOnline(1) {
		t.Fatal("memory presence should expire")
	}
}

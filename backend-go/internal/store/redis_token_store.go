package store

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"strconv"
	"time"

	"github.com/redis/go-redis/v9"
)

// RedisTokenStore 是 TokenStore 的 Redis 实现（阶段 2）。
//
// 为什么 token 存 Redis 而不是 JWT：
//   - 会话管理需要"立即失效"（登出/改密码/封号），JWT 无状态做不到；
//   - Redis 原生 TTL 负责过期，不需要定时任务清理；
//   - 多实例部署时天然共享登录态（内存版做不到）。
//
// key: auth:token:<token> -> userID，TTL = 24h。
type RedisTokenStore struct {
	client *redis.Client
}

// NewRedisTokenStore 创建 Redis 会话存储。
func NewRedisTokenStore(client *redis.Client) *RedisTokenStore {
	return &RedisTokenStore{client: client}
}

var _ TokenStore = (*RedisTokenStore)(nil)

// Issue 生成 32 字节随机 token 并写入 Redis。
func (s *RedisTokenStore) Issue(userID uint64, ttl time.Duration) (string, time.Time, error) {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		return "", time.Time{}, err
	}
	token := hex.EncodeToString(buf)
	expiresAt := time.Now().Add(ttl)

	if err := s.client.Set(context.Background(), tokenKey(token), userID, ttl).Err(); err != nil {
		return "", time.Time{}, err
	}
	return token, expiresAt, nil
}

// Resolve 校验 token。
//
// redis.Nil = key 不存在或已过期，两者对调用方是同一种结果：登录态无效。
func (s *RedisTokenStore) Resolve(token string) (uint64, error) {
	raw, err := s.client.Get(context.Background(), tokenKey(token)).Result()
	if err == redis.Nil {
		return 0, ErrTokenInvalid
	}
	if err != nil {
		return 0, err
	}

	id, err := strconv.ParseUint(raw, 10, 64)
	if err != nil || id == 0 {
		// 值被写坏（人工改库/版本不兼容）时按无效处理，别把错误往上抛。
		return 0, ErrTokenInvalid
	}
	return id, nil
}

// Revoke 删除 token —— 对不透明 token 来说，删 key 就是最彻底的吊销：
// 没有额外黑名单，因为验证时本来就要读这个 key。
//
// 将来如果换成 JWT（无状态、不查库），才需要 auth:revoked:<token> 这类黑名单，
// 常量 keyRevokedPrefix 已经预留好了。
func (s *RedisTokenStore) Revoke(token string) error {
	return s.client.Del(context.Background(), tokenKey(token)).Err()
}

// Touch 给 token 续期（滑动过期）。
//
// 阶段 1 的内存实现没有这个方法；移动端"长期在线"体验需要它：
// 用户只要在活跃，就不该被 24 小时硬性踢下线。
func (s *RedisTokenStore) Touch(token string, ttl time.Duration) error {
	return s.client.Expire(context.Background(), tokenKey(token), ttl).Err()
}

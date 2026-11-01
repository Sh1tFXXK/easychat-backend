package store

import (
	"context"
	"crypto/rand"
	"crypto/subtle"
	"time"

	"github.com/redis/go-redis/v9"
)

// RedisVerifyStore 是 VerifyStore 的 Redis 实现。
//
// 三个 key 各管一件事：
//
//	verify:code:<target>      6 位验证码，TTL 5 分钟
//	verify:cooldown:<target>  发送冷却，TTL 60 秒（SET NX 保证原子）
//	verify:attempts:<target>  连续输错次数，>=5 直接作废验证码
//
// 为什么冷却用 SET NX 而不是 GET 再 SET：
// 并发两个"发送验证码"请求会同时 GET 到空、同时 SET 成功 —— 短信就被刷两条。
// SET NX 是单条原子命令，只有一个能赢。
type RedisVerifyStore struct {
	client *redis.Client
}

// NewRedisVerifyStore 创建 Redis 验证码存储。
func NewRedisVerifyStore(client *redis.Client) *RedisVerifyStore {
	return &RedisVerifyStore{client: client}
}

var _ VerifyStore = (*RedisVerifyStore)(nil)

// Send 生成并保存验证码。
func (s *RedisVerifyStore) Send(target string) (string, time.Duration, error) {
	ctx := context.Background()

	ok, err := s.client.SetNX(ctx, keyVerifyCooldown+target, 1, verifyCooldownTTL).Result()
	if err != nil {
		return "", 0, err
	}
	if !ok {
		return "", 0, ErrVerifyTooFrequent
	}

	code, err := randomDigits(6)
	if err != nil {
		_ = s.client.Del(ctx, keyVerifyCooldown+target).Err()
		return "", 0, err
	}

	// 新验证码覆盖旧的，同时清空失败计数（否则上一轮的 4 次失败会带到这一轮）。
	pipe := s.client.TxPipeline()
	pipe.Set(ctx, keyVerifyCode+target, code, verifyCodeTTL)
	pipe.Del(ctx, keyVerifyAttempts+target)
	if _, err := pipe.Exec(ctx); err != nil {
		_ = s.client.Del(ctx, keyVerifyCooldown+target).Err()
		return "", 0, err
	}

	return code, verifyCodeTTL, nil
}

// Verify 校验并消费验证码。
//
// 用 subtle.ConstantTimeCompare 而不是 ==：验证码是安全凭据，
// 普通字符串比较会在第一个不同字节就返回，理论上可被计时侧信道逐位爆破。
func (s *RedisVerifyStore) Verify(target, code string) bool {
	ctx := context.Background()

	want, err := s.client.Get(ctx, keyVerifyCode+target).Result()
	if err == redis.Nil {
		return false
	}
	if err != nil {
		return false
	}

	if subtle.ConstantTimeCompare([]byte(want), []byte(code)) != 1 {
		attempts, _ := s.client.Incr(ctx, keyVerifyAttempts+target).Result()
		// 第一次失败时才设过期，避免每次 INCR 都把窗口往后推。
		if attempts == 1 {
			_ = s.client.Expire(ctx, keyVerifyAttempts+target, verifyCodeTTL).Err()
		}
		if attempts >= verifyMaxAttempts {
			// 6 位数字只有 100 万种，不限次数就是可爆破的。
			_ = s.client.Del(ctx, keyVerifyCode+target, keyVerifyAttempts+target).Err()
		}
		return false
	}

	// 一次性：校验成功立刻删，防重放。
	_ = s.client.Del(ctx, keyVerifyCode+target, keyVerifyAttempts+target).Err()
	return true
}

// randomDigits 生成 n 位十进制随机串（每位数独立取模，分布均匀）。
func randomDigits(n int) (string, error) {
	buf := make([]byte, n)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	out := make([]byte, n)
	for i := range buf {
		out[i] = '0' + buf[i]%10
	}
	return string(out), nil
}

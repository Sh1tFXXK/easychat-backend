package store

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

// Redis key 设计集中放在这里，避免各 store 里散落字符串拼接（改前缀时只改一处）。
//
// 命名规范：<域>:<对象>:<id>，冒号分层，方便用 SCAN/监控按前缀统计。
const (
	keyTokenPrefix    = "auth:token:"      // auth:token:<token>          -> userID
	keyRevokedPrefix  = "auth:revoked:"    // auth:revoked:<token>        -> 1（为将来切 JWT 预留）
	keyVerifyCode     = "verify:code:"     // verify:code:<target>        -> 6 位验证码
	keyVerifyCooldown = "verify:cooldown:" // verify:cooldown:<target>    -> 1
	keyVerifyAttempts = "verify:attempts:" // verify:attempts:<target>    -> 失败次数
	keyUnreadPrefix   = "unread:"          // unread:<userID>             -> hash{conversationID: count}
	keyOnlinePrefix   = "presence:online:" // presence:online:<userID>    -> 1
	keyLastSeenPrefix = "presence:lastseen:"
	keyChatListPrefix = "cache:chatlist:" // cache:chatlist:<userID>     -> []ChatSession JSON
)

// Redis 连接与各 store 的 TTL 常量。集中定义，方便面试时一句话说清"缓存活多久"。
const (
	// tokenTTL 与 auth_handler.go 里签发 token 的有效期保持一致。
	tokenTTL = 24 * time.Hour
	// revokedTokenTTL 是黑名单条目的保留时长；token 本身过期后黑名单也没必要留。
	revokedTokenTTL = tokenTTL

	verifyCodeTTL     = 5 * time.Minute
	verifyCooldownTTL = 60 * time.Second
	verifyMaxAttempts = 5

	// OnlineTTL 是心跳有效期：客户端必须在这段时间内续期，否则视为掉线。
	// 进程崩溃时没人续期，key 自动过期 —— 这是"幽灵在线"的兜底。
	OnlineTTL   = 90 * time.Second
	lastSeenTTL = 7 * 24 * time.Hour

	// unreadTTL 只用于回收长期不活跃用户的计数，避免 hash 无限增长。
	unreadTTL = 30 * 24 * time.Hour
	// chatListTTL 是会话列表缓存的兜底过期时间；正常路径靠发消息时主动失效。
	chatListTTL = 60 * time.Second
)

// RedisOptions 是连接配置。全部来自环境变量，方便 Docker/Compose 注入。
type RedisOptions struct {
	Addr     string
	Password string
	DB       int
}

// NewRedisClient 建立连接并做一次 PING 验证。
//
// 为什么要在这里 Ping：go-redis 的 NewClient 是懒连接，不 Ping 的话
// 配置写错要等到第一个业务请求才炸，而且错误信息会出现在一个完全无关的 handler 里。
func NewRedisClient(ctx context.Context, opt RedisOptions) (*redis.Client, error) {
	client := redis.NewClient(&redis.Options{
		Addr:         opt.Addr,
		Password:     opt.Password,
		DB:           opt.DB,
		DialTimeout:  3 * time.Second,
		ReadTimeout:  2 * time.Second,
		WriteTimeout: 2 * time.Second,
		PoolSize:     20, // 小服务 20 个连接足够；每连接 2 个 fd
	})

	pingCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()
	if err := client.Ping(pingCtx).Err(); err != nil {
		_ = client.Close()
		return nil, fmt.Errorf("redis ping %s: %w", opt.Addr, err)
	}
	return client, nil
}

// 把 code 生成的随机数放在这里，便于测试替换？不需要 —— crypto/rand 不可预测是底线。
func tokenKey(token string) string   { return keyTokenPrefix + token }
func revokedKey(token string) string { return keyRevokedPrefix + token }
func unreadKey(userID uint64) string { return fmt.Sprintf("%s%d", keyUnreadPrefix, userID) }
func onlineKey(userID uint64) string { return fmt.Sprintf("%s%d", keyOnlinePrefix, userID) }
func lastSeenKey(userID uint64) string {
	return fmt.Sprintf("%s%d", keyLastSeenPrefix, userID)
}
func chatListKey(userID uint64) string { return fmt.Sprintf("%s%d", keyChatListPrefix, userID) }

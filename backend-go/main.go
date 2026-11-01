package main

import (
	"context"
	"log"
	"os"
	"strconv"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/router"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
)

func main() {
	// 默认全部内存实现：不配 Redis 也能 `go run .` 直接跑起来（本地开发/单测友好）。
	deps := router.Deps{
		Users:         store.NewInMemoryUserStore(),
		Messages:      store.NewInMemoryMessageStore(),
		Conversations: store.NewInMemoryConversationStore(),
		Groups:        store.NewInMemoryGroupStore(),
		Favorites:     store.NewInMemoryFavoriteStore(),
		Attention:     store.NewInMemorySpecialAttentionStore(),
		Friends:       store.NewInMemoryFriendStore(),
		Verify:        store.NewInMemoryVerifyStore(),
		Tokens:        store.NewInMemoryTokenStore(),
		Notifications: store.NewInMemoryNotificationStore(),
		Errors:        store.NewInMemoryErrorStore(),

		Unread:   store.NewInMemoryUnreadStore(),
		Presence: store.NewInMemoryPresenceStore(),
		ChatList: store.NewInMemoryConversationCache(time.Minute),
	}

	// 配了 REDIS_ADDR 就切到 Redis 实现（会话/验证码/未读/在线状态/会话列表缓存）。
	//
	// 为什么失败不直接退出：Redis 是加速与共享层，进程本身还能提供内存版服务；
	// 生产环境应该反过来 —— 启动失败即退出（fail fast），避免多实例状态不一致。
	// 这里用 ENV 控制：
	//   REDIS_REQUIRED=true  -> 连不上就退出（生产）
	//   默认                 -> 降级到内存并打警告（本地）
	if addr := os.Getenv("REDIS_ADDR"); addr != "" {
		client, err := store.NewRedisClient(context.Background(), store.RedisOptions{
			Addr:     addr,
			Password: os.Getenv("REDIS_PASSWORD"),
			DB:       envInt("REDIS_DB", 0),
		})
		if err != nil {
			if os.Getenv("REDIS_REQUIRED") == "true" {
				log.Fatalf("redis required but unavailable: %v", err)
			}
			log.Printf("[warn] redis unavailable (%v), falling back to in-memory stores", err)
		} else {
			defer client.Close()
			deps.Tokens = store.NewRedisTokenStore(client)
			deps.Verify = store.NewRedisVerifyStore(client)
			deps.Unread = store.NewRedisUnreadStore(client)
			deps.Presence = store.NewRedisPresenceStore(client)
			deps.ChatList = store.NewRedisConversationCache(client, time.Minute)
			log.Printf("redis connected: %s db=%d", addr, envInt("REDIS_DB", 0))
		}
	} else {
		log.Println("[info] REDIS_ADDR not set, using in-memory stores")
	}

	r := router.SetupRouter(deps)

	log.Println("easychat-go backend listening on :8080")
	if err := r.Run(":8080"); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}

// envInt 读取整型环境变量，缺省或非法时返回默认值。
func envInt(key string, def int) int {
	raw := os.Getenv(key)
	if raw == "" {
		return def
	}
	v, err := strconv.Atoi(raw)
	if err != nil {
		return def
	}
	return v
}

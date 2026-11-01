package main

import (
	"log"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/router"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
)

func main() {
	// 阶段 1 全部使用内存实现：接口层已经定死，换成 MySQL/Redis 实现时
	// handler 和 router 一行不用改（这就是先定 store 接口的意义）。
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
	}

	r := router.SetupRouter(deps)

	log.Println("easychat-go backend listening on :8080")
	if err := r.Run(":8080"); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}

package router

import (
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/handler"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// SetupRouter 装配全部 API 路由。
//
// 路由层级约定（与 Java 版 controller 对齐）：
//
//	/api/user/...      用户
//	/api/messages      消息
//	/api/chat/...      会话   （注意：Java 侧群聊是 /api/group 之类，后续再拆）
//	/api/favorites/... 收藏
//
// 只在这里集中增删路由 —— handler 只负责业务，不自己注册。
func SetupRouter(userStore store.UserStore, messageStore store.MessageStore, favoriteStore store.FavoriteStore) *gin.Engine {
	r := gin.Default()

	// Health check
	r.GET("/health", func(c *gin.Context) {
		c.JSON(200, gin.H{"status": "ok"})
	})
	r.GET("/healthz", func(c *gin.Context) {
		c.JSON(200, gin.H{"status": "ok"})
	})

	userHandler := handler.NewUserHandler(userStore)
	messageHandler := handler.NewMessageHandler(messageStore)
	chatHandler := handler.NewChatHandler(messageStore)
	favoriteHandler := handler.NewFavoriteHandler(favoriteStore)

	api := r.Group("/api")
	{
		// ---------------- 用户 ----------------
		user := api.Group("/user")
		{
			user.POST("/register", userHandler.Register)
			user.POST("/login", userHandler.Login)
			user.GET("/:id", userHandler.GetUser)
			user.POST("/username/validate", userHandler.ValidateUsername)
			user.GET("/search", userHandler.SearchUser)
		}

		// ---------------- 消息 ----------------
		messages := api.Group("/messages")
		{
			messages.POST("", messageHandler.CreateMessage)
			messages.GET("", messageHandler.ListMessages)
		}

		// ---------------- 会话 ----------------
		chat := api.Group("/chat")
		{
			chat.POST("", chatHandler.SendChat)
			chat.GET("/:conversation_id", chatHandler.GetChat)
		}

		// ---------------- 收藏 ----------------
		favorites := api.Group("/favorites")
		{
			favorites.POST("", favoriteHandler.AddFavorite)     // 新增收藏
			favorites.GET("", favoriteHandler.ListFavorites)    // 游标分页列表
			favorites.GET("/:id", favoriteHandler.GetFavorite)  // 详情
			favorites.DELETE("/:id", favoriteHandler.DeleteFavorite) // 逻辑删除
		}
	}

	return r
}

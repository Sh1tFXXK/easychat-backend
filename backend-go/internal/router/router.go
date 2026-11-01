package router

import (
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/handler"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/middleware"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// Deps 汇总所有 store 依赖。
//
// 为什么不用一长串参数：十几个 store 位置传参极易传错（还都是接口类型，
// 编译器也拦不住把 UserStore 传到 MessageStore 的位置），聚合成结构体后
// 具名字段一眼能看出漏了谁。
type Deps struct {
	Users         store.UserStore
	Messages      store.MessageStore
	Conversations store.ConversationStore
	Groups        store.GroupStore
	Favorites     store.FavoriteStore
	Attention     store.SpecialAttentionStore
	Friends       store.FriendStore
	Verify        store.VerifyStore
	Tokens        store.TokenStore
	Notifications store.NotificationStore
	Errors        store.ErrorStore

	// ---- 阶段 2：Redis 支撑的热点数据（内存实现也可注入，方便降级/测试）----
	Unread   store.UnreadStore
	Presence store.PresenceStore
	ChatList store.ConversationCache
}

// SetupRouter 装配全项目路由。
//
// 路径以 Java 版 controller 的 @RequestMapping 为准（前端已按它写死），
// 有前缀分歧的（/auth、/user/notification）做双挂载，兼容 baseURL=/api。
func SetupRouter(d Deps) *gin.Engine {
	r := gin.Default()

	// 认证中间件：解析 Bearer token 并写入上下文。
	// 没有 token 时不拦请求（健康检查/登录/注册是公开的），
	// 需要登录的接口由 handler 里的 requireUser 兜底。
	r.Use(middleware.ResolveUser(d.Tokens))

	// 静态文件：头像/图片/文件。
	r.Static("/uploads", "./uploads")

	// ---------------- 健康检查 ----------------
	r.GET("/health", func(c *gin.Context) { c.JSON(200, gin.H{"status": "ok"}) })
	r.GET("/healthz", func(c *gin.Context) { c.JSON(200, gin.H{"status": "ok"}) })

	// ---------------- handler 实例化 ----------------
	authHandler := handler.NewAuthHandler(d.Users, d.Tokens)
	userHandler := handler.NewUserHandler(d.Users, d.Friends, d.Verify)
	chatHandler := handler.NewChatHandler(d.Messages, d.Conversations, d.Users, d.Unread, d.ChatList)
	messageHandler := handler.NewMessageHandler(d.Messages, d.Conversations, d.Unread, d.ChatList)
	groupHandler := handler.NewGroupHandler(d.Groups, d.Conversations, d.Messages, d.Users, d.Unread, d.ChatList)
	favoriteHandler := handler.NewFavoriteHandler(d.Favorites)
	attentionHandler := handler.NewSpecialAttentionHandler(d.Attention, d.Users)
	notificationHandler := handler.NewNotificationHandler(d.Notifications)
	adminHandler := handler.NewAdminHandler(d.Errors)
	presenceHandler := handler.NewPresenceHandler(d.Presence, store.OnlineTTL)

	api := r.Group("/api")

	// ================================================================
	// 认证 /auth
	// ================================================================
	authRoot := r.Group("/auth")
	authAPI := api.Group("/auth")
	for _, g := range []*gin.RouterGroup{authRoot, authAPI} {
		g.POST("/login", authHandler.Login)
		g.POST("/logout", authHandler.Logout)
	}

	// ================================================================
	// 用户 /api/user
	// ================================================================
	user := api.Group("/user")
	{
		user.POST("/register", userHandler.Register)
		user.POST("/login", userHandler.Login) // 骨架期兼容，正式登录走 /auth/login

		user.POST("/verifyCode/send", userHandler.SendVerifyCode)
		user.POST("/verifyCode/validate", userHandler.ValidateVerifyCode)

		// Java 的怪路径：/api/user/user?id=
		user.GET("/user", userHandler.GetUser)
		user.POST("/user/edit", userHandler.EditUser)
		user.POST("/user/changeAvatar", userHandler.ChangeAvatar)
		user.POST("/user/changePassword", userHandler.ChangePassword)
		user.POST("/user/addTag", userHandler.AddTag)
		user.POST("/user/removeTag", userHandler.RemoveTag)

		user.GET("/search", userHandler.SearchUser)
		user.POST("/username/validate", userHandler.ValidateUsername)
		user.POST("/password/validate", userHandler.ValidatePassword)

		user.GET("/friends", userHandler.GetFriendsList)
		user.GET("/friendVerify", userHandler.GetFriendVerifyList)

		// Go 骨架期的 RESTful 写法，前端切完可删。
		user.GET("/:id", userHandler.GetUser)

		// ---------------- 特别关心 ----------------
		attention := user.Group("/special-attention")
		{
			attention.GET("", attentionHandler.GetAttentionList)
			attention.POST("", attentionHandler.AddAttention)
			attention.PUT("", attentionHandler.UpdateAttention)
			attention.DELETE("", attentionHandler.RemoveAttention)
			attention.DELETE("/by-user", attentionHandler.RemoveAttentionByUser)
			attention.POST("/batch", attentionHandler.BatchOperation)
			attention.GET("/check", attentionHandler.CheckAttentionStatus)
			attention.GET("/updates", attentionHandler.GetAttentionUpdates)
		}
	}

	// ================================================================
	// 会话与历史消息 /api/chat
	// ================================================================
	chat := api.Group("/chat")
	{
		chat.GET("/chats", chatHandler.GetChats)
		chat.GET("/chats/chatHistory", chatHandler.GetChatHistory)
		chat.POST("/chats/savePictureMsg", chatHandler.SavePictureMsg)
		chat.POST("/chats/saveFileMsg", chatHandler.SaveFileMsg)
		chat.POST("/chats/uploadVoice", chatHandler.UploadVoice)

		// 阶段 2：未读数（Redis 计数器）
		chat.GET("/unread", chatHandler.GetUnread)
		chat.POST("/chats/read", chatHandler.MarkRead)
		chat.POST("/chats/readAll", chatHandler.MarkAllRead)
	}

	// ================================================================
	// 在线状态 /api/presence（阶段 2；阶段 3 由 WebSocket 心跳接管）
	// ================================================================
	presence := api.Group("/presence")
	{
		presence.POST("/heartbeat", presenceHandler.Heartbeat)
		presence.POST("/offline", presenceHandler.Offline)
		presence.DELETE("/online", presenceHandler.Offline)
		presence.GET("/status", presenceHandler.GetStatus)
		presence.GET("/online", presenceHandler.BatchStatus)
	}

	// 骨架期消息接口（单聊发送/列表）。
	messages := api.Group("/messages")
	{
		messages.POST("", messageHandler.CreateMessage)
		messages.GET("", messageHandler.ListMessages)
	}

	// ================================================================
	// 群聊 /api/group
	// ================================================================
	group := api.Group("/group")
	{
		group.POST("/groups", groupHandler.CreateGroup)
		group.GET("/groups/:groupId", groupHandler.GetGroupDetail)
		group.PUT("/groups/:groupId", groupHandler.UpdateGroup)
		group.DELETE("/groups/:groupId", groupHandler.DeleteGroup)

		group.GET("/users/:userId/groups", groupHandler.GetUserGroups)

		group.POST("/groups/:groupId/members", groupHandler.InviteMembers)
		group.DELETE("/groups/:groupId/members/:userId", groupHandler.RemoveMember)

		group.GET("/groups/:groupId/messages", groupHandler.GetGroupMessages)

		group.POST("/messages/image", groupHandler.SendGroupImage)
		group.POST("/messages/file", groupHandler.SendGroupFile)
		group.POST("/messages/text", groupHandler.SendGroupText)
	}

	// ================================================================
	// 收藏 /api/favorites（22 个端点，与 Java FavoriteController 一一对应）
	// ================================================================
	favorites := api.Group("/favorites")
	{
		favorites.POST("", favoriteHandler.AddFavorite)
		favorites.GET("", favoriteHandler.ListFavorites)
		favorites.GET("/:id", favoriteHandler.GetFavorite)
		favorites.PUT("/:id", favoriteHandler.UpdateFavorite)
		favorites.DELETE("/:id", favoriteHandler.DeleteFavorite)

		favorites.DELETE("/batch", favoriteHandler.BatchDeleteFavorites)
		favorites.POST("/search", favoriteHandler.AdvancedSearch)
		favorites.PUT("/batch/category", favoriteHandler.BatchUpdateCategory)
		favorites.PUT("/batch/tags/add", favoriteHandler.BatchAddTags)
		favorites.PUT("/batch/tags/remove", favoriteHandler.BatchRemoveTags)
		favorites.GET("/statistics", favoriteHandler.GetStatistics)
		favorites.GET("/check-duplicate", favoriteHandler.CheckDuplicate)

		favorites.POST("/categories", favoriteHandler.CreateCategory)
		favorites.GET("/categories", favoriteHandler.GetCategories)
		favorites.POST("/categories/init", favoriteHandler.InitDefaultCategories)
		favorites.PUT("/categories/:id", favoriteHandler.UpdateCategory)
		favorites.DELETE("/categories/:id", favoriteHandler.DeleteCategory)

		favorites.POST("/tags", favoriteHandler.CreateTag)
		favorites.GET("/tags", favoriteHandler.GetTags)
		favorites.GET("/tags/popular", favoriteHandler.GetPopularTags)
		favorites.DELETE("/tags/:id", favoriteHandler.DeleteTag)
		favorites.DELETE("/tags/cleanup", favoriteHandler.CleanupTags)
	}

	// ================================================================
	// 通知设置 /user/notification（Java 原路径无 /api，双挂载）
	// ================================================================
	notificationRoot := r.Group("/user/notification")
	notificationAPI := api.Group("/user/notification")
	for _, g := range []*gin.RouterGroup{notificationRoot, notificationAPI} {
		g.PUT("/settings", notificationHandler.UpdateSettings)
		g.PUT("/template", notificationHandler.SetTemplate)
		g.POST("/retry", notificationHandler.RetryFailed)
	}

	// ================================================================
	// 错误监控 /admin/error-monitor
	//
	// TODO(安全): 生产环境必须套管理员鉴权中间件。
	// ================================================================
	admin := r.Group("/admin/error-monitor")
	{
		admin.GET("/statistics", adminHandler.GetErrorStatistics)
		admin.GET("/health", adminHandler.GetSystemHealth)
		admin.GET("/circuit-breaker", adminHandler.GetCircuitBreakerStats)
		admin.POST("/circuit-breaker/reset", adminHandler.ResetCircuitBreaker)
		admin.POST("/cleanup", adminHandler.CleanupExpiredData)
		admin.POST("/test-error", adminHandler.TestError)
		admin.GET("/config", adminHandler.GetErrorHandlingConfig)
	}

	return r
}

package router

import (
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/handler"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// SetupRouter 装配全项目路由。
//
// 设计原则：
//  1. 路由只在这里注册 —— handler 不自己挂路由，store 不感知 HTTP；
//  2. 路径以 Java 版 controller 的 @RequestMapping 为准（前端已经按它写死），
//     有前缀分歧的（/auth、/user/notification）做双挂载，兼容前端 baseURL=/api；
//  3. 未实现的 handler 统一返回 501，联调时能一眼看出"路由通、逻辑没写"。
//
// Java 对照表（master 分支）：
//
//	AuthController              /auth
//	UserController              /api/user
//	ChatController              /api/chat
//	GroupChatController         /api/group
//	FavoriteController          /api/favorites
//	SpecialAttentionController  /api/user/special-attention
//	NotificationController      /user/notification
//	ErrorMonitorController      /admin/error-monitor
func SetupRouter(
	userStore store.UserStore,
	messageStore store.MessageStore,
	favoriteStore store.FavoriteStore,
	specialAttentionStore store.SpecialAttentionStore,
) *gin.Engine {
	r := gin.Default()

	// ---------------- 健康检查 ----------------
	r.GET("/health", func(c *gin.Context) { c.JSON(200, gin.H{"status": "ok"}) })
	r.GET("/healthz", func(c *gin.Context) { c.JSON(200, gin.H{"status": "ok"}) })

	// ---------------- handler 实例化（依赖在这里注入，别在 handler 里 new store） ----------------
	authHandler := handler.NewAuthHandler()
	userHandler := handler.NewUserHandler(userStore)
	chatHandler := handler.NewChatHandler(messageStore)
	messageHandler := handler.NewMessageHandler(messageStore)
	groupHandler := handler.NewGroupHandler()
	favoriteHandler := handler.NewFavoriteHandler(favoriteStore)
	specialAttentionHandler := handler.NewSpecialAttentionHandler(specialAttentionStore)
	notificationHandler := handler.NewNotificationHandler()
	adminHandler := handler.NewAdminHandler()

	api := r.Group("/api")

	// ================================================================
	// 认证 /auth
	// ================================================================
	// 双挂载：Java 原路径是 /auth/login；前端 request.js 的 baseURL=/api，
	// 实际请求打到 /api/auth/login。两个都挂，前后端都不用改。
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
		// -- 账号 --
		user.POST("/register", userHandler.Register)
		user.POST("/login", userHandler.Login) // Go 骨架期保留，前端切到 /auth/login 后可下掉

		// -- 验证码 --
		user.POST("/verifyCode/send", userHandler.SendVerifyCode)
		user.POST("/verifyCode/validate", userHandler.ValidateVerifyCode)

		// -- 资料（注意 Java 的怪路径：/api/user/user?id=） --
		user.GET("/user", userHandler.GetUser)
		user.POST("/user/edit", userHandler.EditUser)
		user.POST("/user/changeAvatar", userHandler.ChangeAvatar)
		user.POST("/user/changePassword", userHandler.ChangePassword)
		user.POST("/user/addTag", userHandler.AddTag)
		user.POST("/user/removeTag", userHandler.RemoveTag)

		// -- 查询与安全 --
		user.GET("/search", userHandler.SearchUser)
		user.POST("/username/validate", userHandler.ValidateUsername)
		user.POST("/password/validate", userHandler.ValidatePassword)

		// -- 好友 --
		user.GET("/friends", userHandler.GetFriendsList)
		user.GET("/friendVerify", userHandler.GetFriendVerifyList)

		// -- Go 骨架期保留的 RESTful 写法，Java 没有；前端切完可删 --
		user.GET("/:id", userHandler.GetUser)

		// ============================================================
		// 特别关心 /api/user/special-attention
		// ============================================================
		attention := user.Group("/special-attention")
		{
			attention.GET("", specialAttentionHandler.GetAttentionList)
			attention.POST("", specialAttentionHandler.AddAttention)
			attention.PUT("", specialAttentionHandler.UpdateAttention)
			attention.DELETE("", specialAttentionHandler.RemoveAttention)
			attention.DELETE("/by-user", specialAttentionHandler.RemoveAttentionByUser)
			attention.POST("/batch", specialAttentionHandler.BatchOperation)
			attention.GET("/check", specialAttentionHandler.CheckAttentionStatus)
			attention.GET("/updates", specialAttentionHandler.GetAttentionUpdates)
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
	}

	// Go 骨架期的消息接口（单聊发送/列表），Java 版没有这两个路径。
	// 阶段 1 验证游标分页用的就是它们，暂时保留。
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
		// -- 基础 CRUD（已实现） --
		favorites.POST("", favoriteHandler.AddFavorite)
		favorites.GET("", favoriteHandler.ListFavorites)
		favorites.GET("/:id", favoriteHandler.GetFavorite)
		favorites.PUT("/:id", favoriteHandler.UpdateFavorite)
		favorites.DELETE("/:id", favoriteHandler.DeleteFavorite)

		// -- 批量与搜索 --
		favorites.DELETE("/batch", favoriteHandler.BatchDeleteFavorites)
		favorites.POST("/search", favoriteHandler.AdvancedSearch)
		favorites.PUT("/batch/category", favoriteHandler.BatchUpdateCategory)
		favorites.PUT("/batch/tags/add", favoriteHandler.BatchAddTags)
		favorites.PUT("/batch/tags/remove", favoriteHandler.BatchRemoveTags)
		favorites.GET("/statistics", favoriteHandler.GetStatistics)
		favorites.GET("/check-duplicate", favoriteHandler.CheckDuplicate)

		// -- 分类 --
		favorites.POST("/categories", favoriteHandler.CreateCategory)
		favorites.GET("/categories", favoriteHandler.GetCategories)
		favorites.POST("/categories/init", favoriteHandler.InitDefaultCategories)
		favorites.PUT("/categories/:id", favoriteHandler.UpdateCategory)
		favorites.DELETE("/categories/:id", favoriteHandler.DeleteCategory)

		// -- 标签 --
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
	// 安全提醒：这一组必须在实现时套管理员鉴权中间件（当前是裸路由，只能本机调试用）。
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

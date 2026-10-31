package router

import (
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/handler"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// SetupRouter builds the Gin engine with all API routes mounted.
func SetupRouter(userStore store.UserStore) *gin.Engine {
	r := gin.Default()

	// Health check
	r.GET("/health", func(c *gin.Context) {
		c.JSON(200, gin.H{"status": "ok"})
	})

	userHandler := handler.NewUserHandler(userStore)

	api := r.Group("/api")
	{
		user := api.Group("/user")
		{
			user.POST("/register", userHandler.Register)
			user.POST("/login", userHandler.Login)
			user.GET("/:id", userHandler.GetUser)
			user.POST("/username/validate", userHandler.ValidateUsername)
			user.GET("/search", userHandler.SearchUser)
		}
	}

	return r
}

package handler

import (
	"net/http"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// NotificationHandler 对应 Java 的 NotificationController。
//
// 路径坑：Java 类上是 @RequestMapping("/user/notification")，没有 /api 前缀，
// 而前端 baseURL 是 /api —— router 里两个前缀都挂。
type NotificationHandler struct {
	store store.NotificationStore
}

// NewNotificationHandler 注入通知存储。
func NewNotificationHandler(s store.NotificationStore) *NotificationHandler {
	return &NotificationHandler{store: s}
}

// UpdateSettings PUT /user/notification/settings
//
// PATCH 语义：只改客户端传了的开关。
func (h *NotificationHandler) UpdateSettings(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.UpdateNotificationSettingsRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	c.JSON(http.StatusOK, response.Success(h.store.UpdateSettings(userID, req)))
}

// SetTemplate PUT /user/notification/template
func (h *NotificationHandler) SetTemplate(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.SetTemplateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	c.JSON(http.StatusOK, response.Success(h.store.SetTemplate(userID, req)))
}

// RetryFailed POST /user/notification/retry
//
// 返回本次重投条数。阶段 4 接 Kafka 后，这里改为把失败消息重新投递到 topic。
func (h *NotificationHandler) RetryFailed(c *gin.Context) {
	if _, ok := requireUser(c); !ok {
		return
	}
	n := h.store.RetryFailed()
	c.JSON(http.StatusOK, response.Success(gin.H{"retried": n}))
}

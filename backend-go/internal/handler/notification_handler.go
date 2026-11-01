package handler

import (
	"github.com/gin-gonic/gin"
)

// NotificationHandler 对应 Java 的 NotificationController。
//
// 路径坑：Java 类上是 @RequestMapping("/user/notification")，没有 /api 前缀，
// 而前端 baseURL 是 /api —— router 里两个前缀都挂，谁先通谁上线。
type NotificationHandler struct {
	// TODO(你来实现时再加字段): notifyStore、templateStore ...
}

// NewNotificationHandler 创建 NotificationHandler。
func NewNotificationHandler() *NotificationHandler {
	return &NotificationHandler{}
}

// UpdateSettings PUT /user/notification/settings
// TODO: 通知开关是"读-改-写"，注意并发覆盖问题（前端可能只传部分字段）。
func (h *NotificationHandler) UpdateSettings(c *gin.Context) {
	notImplemented(c, "NotificationHandler.UpdateSettings")
}

// SetTemplate PUT /user/notification/template
// TODO: 模板文本允许变量占位符，但发送前要做转义/长度校验，避免模板注入。
func (h *NotificationHandler) SetTemplate(c *gin.Context) {
	notImplemented(c, "NotificationHandler.SetTemplate")
}

// RetryFailed POST /user/notification/retry
// TODO: 重试要有上限 + 退避，别写成"失败就无限重试"——那是把下游打死的最快方式。
func (h *NotificationHandler) RetryFailed(c *gin.Context) {
	notImplemented(c, "NotificationHandler.RetryFailed")
}

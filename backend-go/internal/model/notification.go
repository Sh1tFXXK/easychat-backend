package model

import "time"

// NotificationSettings 对应用户的通知设置（Java 侧存在 user_setting / 配置表）。
type NotificationSettings struct {
	UserID uint64 `json:"user_id"`
	// EnableAll 是总开关：关掉后下面四个开关全部失效。
	EnableAll bool `json:"enable_all"`
	// 分场景开关。
	Message bool `json:"message"`
	Mention bool `json:"mention"`
	System  bool `json:"system"`
	Friend  bool `json:"friend"`
	// DoNotDisturb 免打扰时间段，格式 "23:00-08:00"；空串表示不启用。
	DoNotDisturb string    `json:"do_not_disturb"`
	UpdatedAt    time.Time `json:"updated_at"`
}

// NotificationTemplate 自定义通知文案模板。
type NotificationTemplate struct {
	UserID    uint64    `json:"user_id"`
	Title     string    `json:"title"`
	Body      string    `json:"body"`
	UpdatedAt time.Time `json:"updated_at"`
}

// UpdateNotificationSettingsRequest 是 PUT /user/notification/settings 的请求体。
// 指针字段 = 只更新客户端真正传了的开关。
type UpdateNotificationSettingsRequest struct {
	EnableAll    *bool   `json:"enable_all"`
	Message      *bool   `json:"message"`
	Mention      *bool   `json:"mention"`
	System       *bool   `json:"system"`
	Friend       *bool   `json:"friend"`
	DoNotDisturb *string `json:"do_not_disturb"`
}

// SetTemplateRequest 是 PUT /user/notification/template 的请求体。
type SetTemplateRequest struct {
	Title string `json:"title" binding:"required,max=64"`
	Body  string `json:"body" binding:"required,max=512"`
}

package model

import "time"

// SpecialAttention 对应 Java 实体 special_attention 表
// （master 分支 src/main/java/org/example/easychat/Entity/SpecialAttention.java）。
//
// 字段命名与 Java 实体逐一对齐，换 MySQL 实现时不用改 VO 转换逻辑。
// 注意：Java 侧 userId / targetUserId 是 String，这里统一成 uint64 ——
// 数据库里本来就是 BIGINT，字符串只是 Java 侧的偷懒写法，别把坏习惯带过来。
type SpecialAttention struct {
	ID           uint64 `json:"id"`
	UserID       uint64 `json:"user_id"`        // 关注方
	TargetUserID uint64 `json:"target_user_id"` // 被关注方

	// 四个通知开关：分别控制上线/下线/新消息/状态变更是否提醒。
	// 为什么不用 tinyint 存一个位图？可读性优先，单条记录字段多一点无所谓。
	OnlineNotification       bool `json:"online_notification"`
	OfflineNotification      bool `json:"offline_notification"`
	MessageNotification      bool `json:"message_notification"`
	StatusChangeNotification bool `json:"status_change_notification"`

	CreateTime time.Time `json:"create_time"`
	UpdateTime time.Time `json:"update_time"`
}

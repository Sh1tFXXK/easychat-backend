package model

import "time"

// 好友关系状态机：
//
//	0 pending  —— 已发送申请，等待对方处理
//	1 accepted —— 已成为好友
//	2 rejected —— 已拒绝
//	3 canceled —— 发起方撤回
const (
	FriendStatusPending  = 0
	FriendStatusAccepted = 1
	FriendStatusRejected = 2
	FriendStatusCanceled = 3
)

// FriendRequest 对应 Java 的 friend_verify 表（好友申请）。
type FriendRequest struct {
	ID         uint64    `json:"id"`
	FromUserID uint64    `json:"from_user_id"`
	ToUserID   uint64    `json:"to_user_id"`
	Message    string    `json:"message"`
	Status     int       `json:"status"`
	CreatedAt  time.Time `json:"created_at"`
	UpdatedAt  time.Time `json:"updated_at"`
}

// Friend 是一条已成立的好友关系（无向，存两份或查两向都可）。
type Friend struct {
	UserID    uint64    `json:"user_id"`
	FriendID  uint64    `json:"friend_id"`
	CreatedAt time.Time `json:"created_at"`
}

// SendFriendRequest 是 POST 好友申请的请求体。
type SendFriendRequest struct {
	ToUserID uint64 `json:"to_user_id" binding:"required"`
	Message  string `json:"message" binding:"max=128"`
}

// HandleFriendRequest 是同意/拒绝好友申请的请求体。
type HandleFriendRequest struct {
	RequestID uint64 `json:"request_id" binding:"required"`
	Accept    bool   `json:"accept"`
}

package model

import "time"

// 会话类型，与 sql/01_core_tables.sql 的 conversations.type 对齐。
const (
	ConversationTypeDirect = 0 // 私聊
	ConversationTypeGroup  = 1 // 群聊
)

// Conversation 对应 conversations 表。
//
// last_msg_id 是冗余字段：会话列表页只需要"最后一条消息"，如果每次都去
// chat_messages 里按会话分组取 MAX(id)，就是典型的 N+1/大范围扫描。
type Conversation struct {
	ID        uint64    `json:"id"`
	Type      int       `json:"type"`
	Title     string    `json:"title"`
	OwnerID   uint64    `json:"owner_id"`
	LastMsgID uint64    `json:"last_msg_id"`
	Members   []uint64  `json:"members,omitempty"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

// ChatSession 是会话列表的一行（GET /api/chat/chats 的返回元素）。
//
// 它不是一个数据库表，而是 conversation + 最后一条消息 + 对端用户信息的聚合结果。
type ChatSession struct {
	ConversationID uint64    `json:"conversation_id"`
	Type           int       `json:"type"`
	Title          string    `json:"title"`
	PeerID         uint64    `json:"peer_id,omitempty"`   // 私聊对端
	PeerName       string    `json:"peer_name,omitempty"` // 私聊对端昵称
	PeerAvatar     string    `json:"peer_avatar,omitempty"`
	LastMessage    *Message  `json:"last_message,omitempty"`
	Unread         int       `json:"unread"`
	UpdatedAt      time.Time `json:"updated_at"`
}

// ChatHistoryQuery 是 GET /api/chat/chats/chatHistory 的查询参数。
type ChatHistoryQuery struct {
	ConversationID uint64 `form:"id"`     // Java 版用 id 表示会话 id
	Cursor         string `form:"cursor"` // 游标：上一页返回的 next_cursor
	Limit          int    `form:"limit"`
}

// UnreadItem 是单个会话的未读数。
type UnreadItem struct {
	ConversationID uint64 `json:"conversation_id"`
	Unread         int    `json:"unread"`
}

// UnreadSummary 是 GET /api/chat/unread 的返回结构。
//
// Total 给 App 角标用（一条汇总数），Items 给会话列表逐条渲染用。
type UnreadSummary struct {
	Total int          `json:"total"`
	Items []UnreadItem `json:"items"`
}

// MarkReadRequest 是"把某个会话标记为已读"的请求体。
type MarkReadRequest struct {
	ConversationID uint64 `json:"conversation_id" binding:"required"`
}

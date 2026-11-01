package model

import "time"

// Message mirrors the `chat_messages` table from sql/01_core_tables.sql.
// `id` is the cursor anchor for cursor-based pagination.
type Message struct {
	ID             uint64     `json:"id"`
	MsgID          string     `json:"msg_id" db:"msg_id"`
	ConversationID uint64     `json:"conversation_id" db:"conversation_id"`
	SenderID       uint64     `json:"sender_id" db:"sender_id"`
	MsgType        int        `json:"msg_type" db:"msg_type"`
	Content        string     `json:"content"`
	Status         int        `json:"status"`
	CreateTime     time.Time  `json:"create_time" db:"create_time"`
}

// MessageListRequest is the query payload for GET /api/messages.
type MessageListRequest struct {
	ConversationID uint64 `json:"conversation_id" form:"conversation_id" binding:"required"`
	Cursor         uint64 `json:"cursor" form:"cursor"`
	Limit          int    `json:"limit" form:"limit"`
}

// MessageCreateRequest is the body payload for POST /api/messages.
type MessageCreateRequest struct {
	ConversationID uint64 `json:"conversation_id" binding:"required"`
	SenderID       uint64 `json:"sender_id" binding:"required"`
	MsgType        int    `json:"msg_type" binding:"required,min=1"`
	Content        string `json:"content" binding:"required"`
}

// MessageListPage is the paginated result returned to the client.
type MessageListPage struct {
	Items      []*Message `json:"items"`
	NextCursor uint64     `json:"next_cursor"`
	HasMore    bool       `json:"has_more"`
}
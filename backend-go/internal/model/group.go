package model

import "time"

// 群成员角色。
const (
	GroupRoleMember = 0 // 普通成员
	GroupRoleAdmin  = 1 // 管理员
	GroupRoleOwner  = 2 // 群主
)

// Group 对应 Java 的 group 表。
type Group struct {
	ID             uint64    `json:"id"`
	ConversationID uint64    `json:"conversation_id"`
	Name           string    `json:"name"`
	Avatar         string    `json:"avatar"`
	Notice         string    `json:"notice"`
	OwnerID        uint64    `json:"owner_id"`
	MemberCount    int       `json:"member_count"`
	CreatedAt      time.Time `json:"created_at"`
	UpdatedAt      time.Time `json:"updated_at"`
}

// GroupMember 对应 group_member 表。
type GroupMember struct {
	GroupID  uint64    `json:"group_id"`
	UserID   uint64    `json:"user_id"`
	Role     int       `json:"role"`
	JoinedAt time.Time `json:"joined_at"`
}

// CreateGroupRequest 是 POST /api/group/groups 的请求体。
// Java 侧 createGroupDto 的字段更多，这里只保留真正用到的部分。
type CreateGroupRequest struct {
	Name      string   `json:"name" binding:"required,min=1,max=64"`
	Avatar    string   `json:"avatar"`
	Notice    string   `json:"notice"`
	MemberIDs []uint64 `json:"member_ids"`
}

// UpdateGroupRequest 是 PUT /api/group/groups/:id 的请求体。
// 用指针实现 PATCH 语义：没传的字段保持原值。
type UpdateGroupRequest struct {
	Name   *string `json:"name"`
	Avatar *string `json:"avatar"`
	Notice *string `json:"notice"`
}

// InviteMembersRequest 是 POST /api/group/groups/:id/members 的请求体。
// Java 版直接用 List<String>，Go 侧包一层更有扩展性（未来可加邀请留言）。
type InviteMembersRequest struct {
	UserIDs []uint64 `json:"user_ids" binding:"required"`
}

// GroupMessagesPage 是群消息分页响应。
type GroupMessagesPage struct {
	Items      []*Message `json:"items"`
	NextCursor string     `json:"next_cursor"`
}

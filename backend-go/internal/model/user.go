package model

import "time"

// User mirrors the `users` table from sql/01_core_tables.sql.
type User struct {
	ID           uint64    `json:"id"`
	Username     string    `json:"username"`
	PasswordHash string    `json:"-" db:"password_hash"`
	Nickname     string    `json:"nickname"`
	Avatar       string    `json:"avatar"`    // 头像 URL
	Signature    string    `json:"signature"` // 个性签名
	Tags         []string  `json:"tags"`      // 用户标签（Java 侧是 user_tag 表）
	Status       int       `json:"status"`    // 1 正常 / 0 禁用
	CreatedAt    time.Time `json:"created_at" db:"created_at"`
	UpdatedAt    time.Time `json:"updated_at" db:"updated_at"`
}

// PublicUser is the safe representation returned to clients (password hash omitted).
type PublicUser struct {
	ID        uint64    `json:"id"`
	Username  string    `json:"username"`
	Nickname  string    `json:"nickname"`
	Avatar    string    `json:"avatar"`
	Signature string    `json:"signature"`
	Tags      []string  `json:"tags"`
	Status    int       `json:"status"`
	CreatedAt time.Time `json:"created_at"`
}

// RegisterRequest is the payload for POST /api/user/register.
type RegisterRequest struct {
	Username string `json:"username" binding:"required,min=3,max=32"`
	Password string `json:"password" binding:"required,min=6,max=64"`
	Nickname string `json:"nickname"`
	Email    string `json:"email"`
}

// LoginRequest is the payload for POST /api/user/login and /auth/login.
type LoginRequest struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
}

// EditUserRequest is the payload for POST /api/user/user/edit.
//
// 全部字段用指针：nil = 客户端没传（保持原值），非 nil 才更新。
// 这就是 PATCH 语义 —— 用值类型会把"没传"当成"清空"，是用户资料接口最常见的 bug。
type EditUserRequest struct {
	Nickname  *string `json:"nickname"`
	Avatar    *string `json:"avatar"`
	Signature *string `json:"signature"`
}

// ChangePasswordRequest is the payload for POST /api/user/user/changePassword.
type ChangePasswordRequest struct {
	OldPassword string `json:"old_password" binding:"required"`
	NewPassword string `json:"new_password" binding:"required,min=6,max=64"`
}

// ValidatePasswordRequest is the payload for POST /api/user/password/validate.
type ValidatePasswordRequest struct {
	Password string `json:"password" binding:"required"`
}

// TagRequest is the payload for addTag / removeTag.
type TagRequest struct {
	Tag string `json:"tag" binding:"required,max=32"`
}

// ToPublic converts a User into a client-safe PublicUser.
func (u *User) ToPublic() PublicUser {
	tags := u.Tags
	if tags == nil {
		tags = []string{} // nil 序列化成 null，前端 map 会崩
	}
	return PublicUser{
		ID:        u.ID,
		Username:  u.Username,
		Nickname:  u.Nickname,
		Avatar:    u.Avatar,
		Signature: u.Signature,
		Tags:      tags,
		Status:    u.Status,
		CreatedAt: u.CreatedAt,
	}
}

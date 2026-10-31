package model

import "time"

// User mirrors the `users` table from sql/01_core_tables.sql.
type User struct {
	ID           uint64     `json:"id"`
	Username     string     `json:"username"`
	PasswordHash string     `json:"-" db:"password_hash"`
	Nickname     string     `json:"nickname"`
	Status       int        `json:"status"`
	CreatedAt    time.Time  `json:"created_at" db:"created_at"`
	UpdatedAt    time.Time  `json:"updated_at" db:"updated_at"`
}

// PublicUser is the safe representation returned to clients (password hash omitted).
type PublicUser struct {
	ID        uint64     `json:"id"`
	Username  string     `json:"username"`
	Nickname  string     `json:"nickname"`
	Status    int        `json:"status"`
	CreatedAt time.Time  `json:"created_at"`
}

// RegisterRequest is the payload for POST /api/user/register.
type RegisterRequest struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required,min=6"`
	Nickname string `json:"nickname"`
}

// LoginRequest is the payload for POST /api/user/login.
type LoginRequest struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
}

// ToPublic converts a User into a client-safe PublicUser.
func (u *User) ToPublic() PublicUser {
	return PublicUser{
		ID:        u.ID,
		Username:  u.Username,
		Nickname:  u.Nickname,
		Status:    u.Status,
		CreatedAt: u.CreatedAt,
	}
}
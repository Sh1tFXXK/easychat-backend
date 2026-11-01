package model

import "time"

// Favorite mirrors the `favorites` table used by the Java original
// (see master branch: src/main/java/org/example/easychat/Entity/Favorite.java).
// Column mapping follows the Java entity so data stays compatible when the
// MySQL-backed store lands.
type Favorite struct {
	ID           uint64    `json:"id"`
	UserID       uint64    `json:"user_id" db:"user_id"`
	ItemType     string    `json:"item_type" db:"item_type"` // MESSAGE / USER / GROUP / FILE
	Title        string    `json:"title"`
	Content      string    `json:"content"`
	OriginalData string    `json:"original_data" db:"original_data"`
	CategoryID   uint64    `json:"category_id" db:"category_id"`
	Tags         string    `json:"tags"`
	Note         string    `json:"note"`
	SourceChatID uint64    `json:"source_chat_id" db:"source_chat_id"`
	IsAvailable  bool      `json:"is_available" db:"is_available"`
	Deleted      bool      `json:"-" db:"deleted"`
	CreateTime   time.Time `json:"create_time" db:"create_time"`
	UpdateTime   time.Time `json:"update_time" db:"update_time"`
}

// AddFavoriteRequest is the body payload for POST /api/favorites.
type AddFavoriteRequest struct {
	ItemType     string `json:"item_type" binding:"required"` // MESSAGE / USER / GROUP / FILE
	Title        string `json:"title"`
	Content      string `json:"content"`
	OriginalData string `json:"original_data"`
	CategoryID   uint64 `json:"category_id"`
	Tags         string `json:"tags"`
	Note         string `json:"note"`
	SourceChatID uint64 `json:"source_chat_id"`
}

// FavoriteListQuery is the query payload for GET /api/favorites.
type FavoriteListQuery struct {
	Cursor     string `form:"cursor"`      // 游标：上一页返回的 next_cursor
	Limit      int    `form:"limit"`       // 每页条数，默认 20
	ItemType   string `form:"item_type"`   // 可选过滤
	CategoryID uint64 `form:"category_id"` // 可选过滤
	Keyword    string `form:"keyword"`     // 可选搜索
}

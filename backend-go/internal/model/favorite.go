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

// FavoriteCategory 对应 favorites_category 表（收藏分类）。
type FavoriteCategory struct {
	ID         uint64    `json:"id"`
	UserID     uint64    `json:"user_id"`
	Name       string    `json:"name"`
	Icon       string    `json:"icon"`
	Sort       int       `json:"sort"`
	Count      int64     `json:"count"` // 该分类下的收藏数（查询时聚合，不落库）
	CreateTime time.Time `json:"create_time"`
}

// FavoriteTag 对应 favorite_tags 表。
type FavoriteTag struct {
	ID         uint64    `json:"id"`
	UserID     uint64    `json:"user_id"`
	Name       string    `json:"name"`
	Count      int64     `json:"count"` // 被多少条收藏使用
	CreateTime time.Time `json:"create_time"`
}

// FavoriteStatistics 是 GET /api/favorites/statistics 的返回结构。
type FavoriteStatistics struct {
	Total      int            `json:"total"`
	ByItemType map[string]int `json:"by_item_type"`
	ByCategory []CategoryStat `json:"by_category"`
}

// CategoryStat 是单个分类的收藏计数。
type CategoryStat struct {
	CategoryID uint64 `json:"category_id"`
	Name       string `json:"name"`
	Count      int    `json:"count"`
}

// FavoriteUpdateRequest 是 PUT /api/favorites/:id 的请求体（PATCH 语义，指针字段=未传不改）。
type FavoriteUpdateRequest struct {
	Title        *string `json:"title"`
	Content      *string `json:"content"`
	OriginalData *string `json:"original_data"`
	Tags         *string `json:"tags"`
	Note         *string `json:"note"`
	CategoryID   *uint64 `json:"category_id"`
	IsAvailable  *bool   `json:"is_available"`
}

// FavoriteSearchRequest 是 POST /api/favorites/search 的请求体。
type FavoriteSearchRequest struct {
	Keyword    string `json:"keyword"`
	ItemType   string `json:"item_type"`
	CategoryID uint64 `json:"category_id"`
	Tag        string `json:"tag"`
	StartTime  string `json:"start_time"` // RFC3339，可空
	EndTime    string `json:"end_time"`
	Cursor     string `json:"cursor"`
	Limit      int    `json:"limit"`
}

// BatchIDsRequest 是批量操作（删除/加标签/移除标签/改分类）的通用请求体。
type BatchIDsRequest struct {
	IDs []uint64 `json:"ids" binding:"required,min=1,max=500"`
}

// BatchCategoryRequest 是 PUT /api/favorites/batch/category 的请求体。
type BatchCategoryRequest struct {
	IDs        []uint64 `json:"ids" binding:"required,min=1,max=500"`
	CategoryID uint64   `json:"category_id"`
}

// BatchTagsRequest 是 PUT /api/favorites/batch/tags/add|remove 的请求体。
type BatchTagsRequest struct {
	IDs  []uint64 `json:"ids" binding:"required,min=1,max=500"`
	Tags []string `json:"tags" binding:"required,min=1,max=20"`
}

// CategoryRequest 是分类的创建/更新请求体。
type CategoryRequest struct {
	Name string `json:"name" binding:"required,max=32"`
	Icon string `json:"icon" binding:"max=64"`
	Sort int    `json:"sort"`
}

// TagRequest 是标签的创建请求体（与用户标签同名不同域，这里叫 FavoriteTagRequest 避免歧义）。
type FavoriteTagRequest struct {
	Name string `json:"name" binding:"required,max=32"`
}

// BatchOperationResult 是批量操作的统一返回结构。
//
// 为什么不用 boolean：批量操作"部分成功"是常态，
// 前端需要知道哪几条失败、为什么失败，否则用户只能看到一句莫名其妙的"操作失败"。
type BatchOperationResult struct {
	SuccessCount int      `json:"success_count"`
	FailedCount  int      `json:"failed_count"`
	FailedIDs    []uint64 `json:"failed_ids,omitempty"`
}

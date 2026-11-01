package handler

import (
	"net/http"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/gin-gonic/gin"
)

// FavoriteHandler is the Go counterpart of the Java FavoriteController.
// 对应 Java: src/main/java/org/example/easychat/controller/FavoriteController.java
//
// NOTE: 目前无 store 依赖，方法体全部留给你实现。等 FavoriteStore 落地后，
// 在这里加上 store 字段并在 NewFavoriteHandler 注入即可（和 UserHandler 一个模式）。
type FavoriteHandler struct {
	// TODO: store store.FavoriteStore
}

// NewFavoriteHandler creates a FavoriteHandler.
func NewFavoriteHandler() *FavoriteHandler {
	return &FavoriteHandler{}
}

// AddFavorite godoc
// POST /api/favorites
// 请求体: model.AddFavoriteRequest
// 你要做: ShouldBindJSON → 校验 item_type 合法 → 落库 → 返回新收藏(200)
func (h *FavoriteHandler) AddFavorite(c *gin.Context) {
	// TODO: implement
	c.JSON(http.StatusNotImplemented, response.Error("AddFavorite: not implemented yet"))
}

// ListFavorites godoc
// GET /api/favorites?cursor=&limit=&item_type=&category_id=&keyword=
// 你要做: 游标分页（参考 sql/02_explain_offset_vs_cursor.sql 的 C 段查询）
//        返回 {"items": [...], "next_cursor": "..."} —— next_cursor 交给前端回传
func (h *FavoriteHandler) ListFavorites(c *gin.Context) {
	// TODO: implement
	c.JSON(http.StatusNotImplemented, response.Error("ListFavorites: not implemented yet"))
}

// GetFavorite godoc
// GET /api/favorites/:id
// 你要做: 校验 id、按 user_id 归属校验（别让别人拿到别人的收藏）、返回详情
func (h *FavoriteHandler) GetFavorite(c *gin.Context) {
	// TODO: implement
	c.JSON(http.StatusNotImplemented, response.Error("GetFavorite: not implemented yet"))
}

// DeleteFavorite godoc
// DELETE /api/favorites/:id
// 你要做: 归属校验 + 逻辑删除（deleted=1），幂等（重复删除不报 500）
func (h *FavoriteHandler) DeleteFavorite(c *gin.Context) {
	// TODO: implement
	c.JSON(http.StatusNotImplemented, response.Error("DeleteFavorite: not implemented yet"))
}

package handler

import (
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// 本文件承接 FavoriteController 里剩余的 18 个接口：批量操作 / 高级搜索 / 分类 / 标签。
//
// 批量接口的统一原则：**先校验归属，再执行**。
// 存储层按 id 逐条判断 user_id，越权/不存在的 id 会进入 failed_ids，
// 而不是静默删掉一部分却整体报成功。

// BatchDeleteFavorites DELETE /api/favorites/batch
func (h *FavoriteHandler) BatchDeleteFavorites(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.BatchIDsRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	res := h.store.BatchDelete(userID, req.IDs)
	c.JSON(http.StatusOK, response.Success(res))
}

// UpdateFavorite PUT /api/favorites/:id
func (h *FavoriteHandler) UpdateFavorite(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	id, ok := parseUintParam(c, "id")
	if !ok {
		return
	}

	var req model.FavoriteUpdateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	fav, err := h.store.Update(userID, id, req)
	if err != nil {
		// 不存在和越权统一 404，防 id 枚举。
		if errors.Is(err, store.ErrFavoriteNotFound) || errors.Is(err, store.ErrFavoriteForbidden) {
			c.JSON(http.StatusNotFound, response.ErrorWithCode("favorite not found", http.StatusNotFound))
			return
		}
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to update favorite", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.Success(fav))
}

// AdvancedSearch POST /api/favorites/search
func (h *FavoriteHandler) AdvancedSearch(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.FavoriteSearchRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}
	req.ItemType = strings.ToUpper(strings.TrimSpace(req.ItemType))

	items, nextCursor, err := h.store.Search(userID, req)
	if err != nil {
		if errors.Is(err, store.ErrFavoriteBadCursor) {
			c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid cursor", http.StatusBadRequest))
			return
		}
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("search failed", http.StatusInternalServerError))
		return
	}
	if items == nil {
		items = []*model.Favorite{}
	}
	c.JSON(http.StatusOK, response.Success(gin.H{"items": items, "next_cursor": nextCursor}))
}

// BatchUpdateCategory PUT /api/favorites/batch/category
func (h *FavoriteHandler) BatchUpdateCategory(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.BatchCategoryRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	res, err := h.store.BatchUpdateCategory(userID, req.IDs, req.CategoryID)
	if err != nil {
		if errors.Is(err, store.ErrCategoryNotFound) {
			c.JSON(http.StatusNotFound, response.ErrorWithCode("category not found", http.StatusNotFound))
			return
		}
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to update category", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.Success(res))
}

// BatchAddTags PUT /api/favorites/batch/tags/add
func (h *FavoriteHandler) BatchAddTags(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.BatchTagsRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}
	c.JSON(http.StatusOK, response.Success(h.store.BatchAddTags(userID, req.IDs, req.Tags)))
}

// BatchRemoveTags PUT /api/favorites/batch/tags/remove
func (h *FavoriteHandler) BatchRemoveTags(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.BatchTagsRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}
	c.JSON(http.StatusOK, response.Success(h.store.BatchRemoveTags(userID, req.IDs, req.Tags)))
}

// GetStatistics GET /api/favorites/statistics
func (h *FavoriteHandler) GetStatistics(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	c.JSON(http.StatusOK, response.Success(h.store.Statistics(userID)))
}

// CheckDuplicate GET /api/favorites/check-duplicate?item_type=&source_chat_id=
func (h *FavoriteHandler) CheckDuplicate(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	itemType := strings.ToUpper(strings.TrimSpace(c.Query("item_type")))
	if itemType == "" {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("item_type is required", http.StatusBadRequest))
		return
	}
	sourceChatID, ok := parseUintQuery(c, "source_chat_id")
	if !ok {
		return
	}

	dup := h.store.CheckDuplicate(userID, itemType, sourceChatID)
	c.JSON(http.StatusOK, response.Success(gin.H{"duplicate": dup}))
}

// CreateCategory POST /api/favorites/categories
func (h *FavoriteHandler) CreateCategory(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.CategoryRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	cat, err := h.store.CreateCategory(userID, req)
	if err != nil {
		if errors.Is(err, store.ErrCategoryDuplicate) {
			c.JSON(http.StatusConflict, response.ErrorWithCode("分类名已存在", http.StatusConflict))
			return
		}
		c.JSON(http.StatusBadRequest, response.ErrorWithCode(err.Error(), http.StatusBadRequest))
		return
	}
	c.JSON(http.StatusOK, response.Success(cat))
}

// UpdateCategory PUT /api/favorites/categories/:id
func (h *FavoriteHandler) UpdateCategory(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	id, ok := parseUintParam(c, "id")
	if !ok {
		return
	}

	var req model.CategoryRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	cat, err := h.store.UpdateCategory(userID, id, req)
	if err != nil {
		switch {
		case errors.Is(err, store.ErrCategoryNotFound):
			c.JSON(http.StatusNotFound, response.ErrorWithCode("category not found", http.StatusNotFound))
		case errors.Is(err, store.ErrCategoryDuplicate):
			c.JSON(http.StatusConflict, response.ErrorWithCode("分类名已存在", http.StatusConflict))
		default:
			c.JSON(http.StatusBadRequest, response.ErrorWithCode(err.Error(), http.StatusBadRequest))
		}
		return
	}
	c.JSON(http.StatusOK, response.Success(cat))
}

// DeleteCategory DELETE /api/favorites/categories/:id
//
// 策略：分类下的收藏移到「未分类」(category_id=0)，不连带删除收藏。
func (h *FavoriteHandler) DeleteCategory(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	id, ok := parseUintParam(c, "id")
	if !ok {
		return
	}

	if err := h.store.DeleteCategory(userID, id); err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("category not found", http.StatusNotFound))
		return
	}
	c.JSON(http.StatusOK, response.SuccessNoData("category deleted"))
}

// GetCategories GET /api/favorites/categories
func (h *FavoriteHandler) GetCategories(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	c.JSON(http.StatusOK, response.Success(h.store.ListCategories(userID)))
}

// InitDefaultCategories POST /api/favorites/categories/init
func (h *FavoriteHandler) InitDefaultCategories(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	added := h.store.InitDefaultCategories(userID)
	c.JSON(http.StatusOK, response.Success(gin.H{"created": added}))
}

// CreateTag POST /api/favorites/tags
func (h *FavoriteHandler) CreateTag(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.FavoriteTagRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	tag, err := h.store.CreateTag(userID, req.Name)
	if err != nil {
		if errors.Is(err, store.ErrTagDuplicate) {
			c.JSON(http.StatusConflict, response.ErrorWithCode("标签已存在", http.StatusConflict))
			return
		}
		c.JSON(http.StatusBadRequest, response.ErrorWithCode(err.Error(), http.StatusBadRequest))
		return
	}
	c.JSON(http.StatusOK, response.Success(tag))
}

// DeleteTag DELETE /api/favorites/tags/:id
func (h *FavoriteHandler) DeleteTag(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	id, ok := parseUintParam(c, "id")
	if !ok {
		return
	}

	if err := h.store.DeleteTag(userID, id); err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("tag not found", http.StatusNotFound))
		return
	}
	c.JSON(http.StatusOK, response.SuccessNoData("tag deleted"))
}

// GetTags GET /api/favorites/tags
func (h *FavoriteHandler) GetTags(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	c.JSON(http.StatusOK, response.Success(h.store.ListTags(userID)))
}

// GetPopularTags GET /api/favorites/tags/popular?limit=
func (h *FavoriteHandler) GetPopularTags(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	limit := 10
	if raw := c.Query("limit"); raw != "" {
		if v, err := strconv.Atoi(raw); err == nil {
			limit = v
		}
	}
	c.JSON(http.StatusOK, response.Success(h.store.PopularTags(userID, limit)))
}

// CleanupTags DELETE /api/favorites/tags/cleanup
func (h *FavoriteHandler) CleanupTags(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	removed := h.store.CleanupTags(userID)
	c.JSON(http.StatusOK, response.Success(gin.H{"removed": removed}))
}

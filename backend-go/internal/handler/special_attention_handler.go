package handler

import (
	"errors"
	"net/http"
	"strconv"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// SpecialAttentionHandler 对应 Java 的 SpecialAttentionController。
//
// 实际路径：/api/user/special-attention（不是 /api/special-attention）
// —— 以 controller 的 @RequestMapping 为准。
//
// 结构和收藏模块完全一致：handler 只做 HTTP 语义，归属校验在 store 里。
type SpecialAttentionHandler struct {
	store store.SpecialAttentionStore
	users store.UserStore
}

// NewSpecialAttentionHandler 注入存储。
func NewSpecialAttentionHandler(s store.SpecialAttentionStore, users store.UserStore) *SpecialAttentionHandler {
	return &SpecialAttentionHandler{store: s, users: users}
}

// attentionItem 是列表项：关注关系 + 对方资料（前端要显示昵称/头像）。
type attentionItem struct {
	*model.SpecialAttention
	Target *model.PublicUser `json:"target,omitempty"`
}

// GetAttentionList GET /api/user/special-attention?cursor=&limit=
func (h *SpecialAttentionHandler) GetAttentionList(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	limit := 20
	if raw := c.Query("limit"); raw != "" {
		if v, err := strconv.Atoi(raw); err == nil {
			limit = v
		}
	}

	items, nextCursor, err := h.store.ListByUser(userID, c.Query("cursor"), limit)
	if err != nil {
		if errors.Is(err, store.ErrFavoriteBadCursor) {
			c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid cursor", http.StatusBadRequest))
			return
		}
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to list special attention", http.StatusInternalServerError))
		return
	}

	// 批量取对方资料，避免 N+1。
	ids := make([]uint64, 0, len(items))
	for _, it := range items {
		ids = append(ids, it.TargetUserID)
	}
	users, _ := h.users.ListByIDs(ids)
	byID := make(map[uint64]model.PublicUser, len(users))
	for _, u := range users {
		byID[u.ID] = u.ToPublic()
	}

	out := make([]attentionItem, 0, len(items))
	for _, it := range items {
		row := attentionItem{SpecialAttention: it}
		if u, ok := byID[it.TargetUserID]; ok {
			row.Target = &u
		}
		out = append(out, row)
	}

	c.JSON(http.StatusOK, response.Success(gin.H{
		"items":       out,
		"next_cursor": nextCursor,
	}))
}

// AddAttention POST /api/user/special-attention
//
// 三种拒绝：关注自己、目标不存在、重复关注（重复关注按幂等成功处理）。
func (h *SpecialAttentionHandler) AddAttention(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req struct {
		TargetUserID uint64 `json:"target_user_id" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}
	if req.TargetUserID == userID {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("cannot follow yourself", http.StatusBadRequest))
		return
	}
	if _, err := h.users.GetByID(req.TargetUserID); err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("target user not found", http.StatusNotFound))
		return
	}

	item, err := h.store.Add(userID, req.TargetUserID)
	if errors.Is(err, store.ErrAttentionDuplicate) {
		// 幂等：已经关注过就直接返回现有关系。
		item, err = h.store.Get(userID, req.TargetUserID)
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to add special attention", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.Success(item))
}

// RemoveAttention DELETE /api/user/special-attention?target_user_id=
func (h *SpecialAttentionHandler) RemoveAttention(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	targetID, ok := parseUintQuery(c, "target_user_id")
	if !ok {
		return
	}
	if targetID == 0 {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("target_user_id is required", http.StatusBadRequest))
		return
	}

	if err := h.store.Remove(userID, targetID); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to remove special attention", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.SuccessNoData("removed"))
}

// RemoveAttentionByUser DELETE /api/user/special-attention/by-user
//
// 批量取消关注。列表长度上限 500，防止一次传十万个 id 打爆存储层。
func (h *SpecialAttentionHandler) RemoveAttentionByUser(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req struct {
		TargetUserIDs []uint64 `json:"target_user_ids" binding:"required,min=1,max=500"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	if err := h.store.RemoveByTargets(userID, req.TargetUserIDs); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to remove", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.Success(gin.H{"removed": len(req.TargetUserIDs)}))
}

// UpdateAttention PUT /api/user/special-attention
//
// 用 *bool 区分"没传"和"传了 false" —— 值类型会把没传的开关误关掉。
func (h *SpecialAttentionHandler) UpdateAttention(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req struct {
		TargetUserID             uint64 `json:"target_user_id" binding:"required"`
		OnlineNotification       *bool  `json:"online_notification"`
		OfflineNotification      *bool  `json:"offline_notification"`
		MessageNotification      *bool  `json:"message_notification"`
		StatusChangeNotification *bool  `json:"status_change_notification"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	cur, err := h.store.Get(userID, req.TargetUserID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("special attention not found", http.StatusNotFound))
		return
	}
	// 先取现有值作为默认，再覆盖客户端传了的开关。
	online := cur.OnlineNotification
	offline := cur.OfflineNotification
	message := cur.MessageNotification
	statusChange := cur.StatusChangeNotification
	if req.OnlineNotification != nil {
		online = *req.OnlineNotification
	}
	if req.OfflineNotification != nil {
		offline = *req.OfflineNotification
	}
	if req.MessageNotification != nil {
		message = *req.MessageNotification
	}
	if req.StatusChangeNotification != nil {
		statusChange = *req.StatusChangeNotification
	}

	item, err := h.store.UpdateSettings(userID, req.TargetUserID, online, offline, message, statusChange)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("special attention not found", http.StatusNotFound))
		return
	}
	c.JSON(http.StatusOK, response.Success(item))
}

// BatchOperation POST /api/user/special-attention/batch
//
// operation 支持 add / remove，返回逐条结果，而不是一句 "ok"。
func (h *SpecialAttentionHandler) BatchOperation(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req struct {
		Operation     string   `json:"operation" binding:"required,oneof=add remove"`
		TargetUserIDs []uint64 `json:"target_user_ids" binding:"required,min=1,max=500"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	res := model.BatchOperationResult{}
	for _, target := range req.TargetUserIDs {
		if target == userID {
			res.FailedCount++
			res.FailedIDs = append(res.FailedIDs, target)
			continue
		}
		switch req.Operation {
		case "add":
			if _, err := h.store.Add(userID, target); err != nil && !errors.Is(err, store.ErrAttentionDuplicate) {
				res.FailedCount++
				res.FailedIDs = append(res.FailedIDs, target)
				continue
			}
		case "remove":
			if err := h.store.Remove(userID, target); err != nil {
				res.FailedCount++
				res.FailedIDs = append(res.FailedIDs, target)
				continue
			}
		}
		res.SuccessCount++
	}
	c.JSON(http.StatusOK, response.Success(res))
}

// CheckAttentionStatus GET /api/user/special-attention/check?target_user_id=
func (h *SpecialAttentionHandler) CheckAttentionStatus(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	targetID, ok := parseUintQuery(c, "target_user_id")
	if !ok {
		return
	}
	if targetID == 0 {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("target_user_id is required", http.StatusBadRequest))
		return
	}

	_, err := h.store.Get(userID, targetID)
	isAttention := err == nil
	c.JSON(http.StatusOK, response.Success(gin.H{"is_attention": isAttention}))
}

// GetAttentionUpdates GET /api/user/special-attention/updates
//
// 拉取最近有更新的关注关系。阶段 1 用内存实现（按 id 倒序取前 20），
// MySQL 版应该按 update_time 做时间窗口 + 游标分页。
func (h *SpecialAttentionHandler) GetAttentionUpdates(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	items, _, err := h.store.ListByUser(userID, "", 20)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to list updates", http.StatusInternalServerError))
		return
	}

	out := make([]attentionItem, 0, len(items))
	for _, it := range items {
		out = append(out, attentionItem{SpecialAttention: it})
	}
	c.JSON(http.StatusOK, response.Success(out))
}

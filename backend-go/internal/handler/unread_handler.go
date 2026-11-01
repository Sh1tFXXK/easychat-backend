package handler

import (
	"net/http"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/gin-gonic/gin"
)

// 本文件是阶段 2 新增的"未读数"接口。
//
// 读未读走 Redis，写未读在 markMessageSent 里完成；
// MySQL 里没有任何 unread 字段 —— 这是有意的，避免每条消息都去 UPDATE 行。

// GetUnread GET /api/chat/unread
//
// 返回当前用户所有会话的未读 + 总数（App 角标）。
// 只查一次 HGETALL，不按会话循环查询。
func (h *ChatHandler) GetUnread(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	convs := h.convs.ListByUser(userID)
	ids := make([]uint64, 0, len(convs))
	for _, conv := range convs {
		ids = append(ids, conv.ID)
	}

	counts := h.unread.GetBatch(userID, ids)

	summary := model.UnreadSummary{Items: make([]model.UnreadItem, 0, len(ids))}
	for _, cid := range ids {
		n := int(counts[cid])
		if n <= 0 {
			continue // 未读为 0 的会话不返回，减小响应体
		}
		summary.Total += n
		summary.Items = append(summary.Items, model.UnreadItem{ConversationID: cid, Unread: n})
	}

	c.JSON(http.StatusOK, response.Success(summary))
}

// MarkRead POST /api/chat/chats/read
//
// 用户打开会话时调用。先校验成员身份，再清零 ——
// 顺序反了就是"任何人可以把别人的未读清掉"（虽然影响不大，但仍是越权）。
func (h *ChatHandler) MarkRead(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.MarkReadRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}
	if !h.convs.IsMember(req.ConversationID, userID) {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("conversation not found", http.StatusNotFound))
		return
	}

	if err := h.unread.Reset(req.ConversationID, userID); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to reset unread", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.Success(gin.H{
		"conversation_id": req.ConversationID,
		"unread":          0,
	}))
}

// MarkAllRead POST /api/chat/chats/readAll
//
// "全部已读"是列表页常见操作。逐个 Reset 是 N 次往返，
// Redis 实现里可以后续优化成一条 Lua/HDEL 批量；会话数通常几十个，先保持简单。
func (h *ChatHandler) MarkAllRead(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	convs := h.convs.ListByUser(userID)
	cleared := 0
	for _, conv := range convs {
		if err := h.unread.Reset(conv.ID, userID); err == nil {
			cleared++
		}
	}
	c.JSON(http.StatusOK, response.Success(gin.H{"cleared": cleared}))
}

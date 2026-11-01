package handler

import (
	"net/http"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// MessageHandler handles chat message endpoints with cursor pagination.
type MessageHandler struct {
	store  store.MessageStore
	convs  store.ConversationStore
	unread store.UnreadStore
	cache  store.ConversationCache
}

// NewMessageHandler creates a MessageHandler with its dependencies.
func NewMessageHandler(
	s store.MessageStore,
	convs store.ConversationStore,
	unread store.UnreadStore,
	cache store.ConversationCache,
) *MessageHandler {
	return &MessageHandler{store: s, convs: convs, unread: unread, cache: cache}
}

// CreateMessage godoc
// @Summary      Send a new message
// @Router       /api/messages [post]
//
// 安全提醒：发送者必须以登录态为准，不能信 body 里的 sender_id ——
// 否则任何登录用户都能伪装成别人发消息。
func (h *MessageHandler) CreateMessage(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.MessageCreateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Error("invalid request body: "+err.Error()))
		return
	}

	// 必须是会话成员，否则等于往别人的私聊里写消息。
	if !h.convs.IsMember(req.ConversationID, userID) {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("conversation not found", http.StatusNotFound))
		return
	}

	msg := &model.Message{
		ConversationID: req.ConversationID,
		SenderID:       userID,
		MsgType:        req.MsgType,
		Content:        req.Content,
		Status:         0, // 0 = delivered
	}
	if err := h.store.Create(msg); err != nil {
		c.JSON(http.StatusInternalServerError, response.Error("failed to create message"))
		return
	}
	h.convs.Touch(req.ConversationID, msg.ID)
	markMessageSent(h.convs, h.unread, h.cache, req.ConversationID, userID)

	c.JSON(http.StatusCreated, response.Success(msg))
}

// ListMessages godoc
// @Summary      List messages for a conversation (cursor pagination)
// @Router       /api/messages [get]
func (h *MessageHandler) ListMessages(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.MessageListRequest
	if err := c.ShouldBindQuery(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Error("invalid query params: "+err.Error()))
		return
	}
	if !h.convs.IsMember(req.ConversationID, userID) {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("conversation not found", http.StatusNotFound))
		return
	}

	if req.Limit <= 0 {
		req.Limit = 20
	}
	if req.Limit > 100 {
		req.Limit = 100
	}

	items, nextCursor, hasMore := h.store.GetByConversation(req.ConversationID, req.Cursor, req.Limit)

	c.JSON(http.StatusOK, response.Success(model.MessageListPage{
		Items:      items,
		NextCursor: nextCursor,
		HasMore:    hasMore,
	}))
}

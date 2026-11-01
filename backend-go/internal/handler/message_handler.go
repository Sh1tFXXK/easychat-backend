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
	store store.MessageStore
}

// NewMessageHandler creates a MessageHandler backed by the given MessageStore.
func NewMessageHandler(s store.MessageStore) *MessageHandler {
	return &MessageHandler{store: s}
}

// CreateMessage godoc
// @Summary      Send a new message
// @Router       /api/messages [post]
func (h *MessageHandler) CreateMessage(c *gin.Context) {
	var req model.MessageCreateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Error("invalid request body: "+err.Error()))
		return
	}

	msg := &model.Message{
		ConversationID: req.ConversationID,
		SenderID:       req.SenderID,
		MsgType:        req.MsgType,
		Content:        req.Content,
		Status:         0, // 0 = delivered
	}
	if err := h.store.Create(msg); err != nil {
		c.JSON(http.StatusInternalServerError, response.Error("failed to create message"))
		return
	}

	c.JSON(http.StatusCreated, response.Success(msg))
}

// ListMessages godoc
// @Summary      List messages for a conversation (cursor pagination)
// @Router       /api/messages [get]
func (h *MessageHandler) ListMessages(c *gin.Context) {
	var req model.MessageListRequest
	if err := c.ShouldBindQuery(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Error("invalid query params: "+err.Error()))
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
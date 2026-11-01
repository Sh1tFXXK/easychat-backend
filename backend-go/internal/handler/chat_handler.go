package handler

import (
	"fmt"
	"net/http"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// ChatHandler handles chat endpoints (send + list messages with cursor pagination).
type ChatHandler struct {
	store store.MessageStore
}

// NewChatHandler creates a ChatHandler backed by the given MessageStore.
func NewChatHandler(s store.MessageStore) *ChatHandler {
	return &ChatHandler{store: s}
}

// SendChat godoc
// @Summary      Send a new chat message
// @Router       /api/chat [post]
func (h *ChatHandler) SendChat(c *gin.Context) {
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
		c.JSON(http.StatusInternalServerError, response.Error("failed to send message"))
		return
	}

	c.JSON(http.StatusCreated, response.Success(msg))
}

// GetChat godoc
// @Summary      List chat messages for a conversation (cursor pagination)
// @Router       /api/chat/:conversation_id [get]
func (h *ChatHandler) GetChat(c *gin.Context) {
	var id uint64
	if _, err := fmt.Sscanf(c.Param("conversation_id"), "%d", &id); err != nil {
		c.JSON(http.StatusBadRequest, response.Error("invalid conversation_id"))
		return
	}

	cursor := uint64(0)
	if cur := c.Query("cursor"); cur != "" {
		if _, err := fmt.Sscanf(cur, "%d", &cursor); err != nil {
			c.JSON(http.StatusBadRequest, response.Error("invalid cursor"))
			return
		}
	}

	limit := 20
	if lim := c.Query("limit"); lim != "" {
		if _, err := fmt.Sscanf(lim, "%d", &limit); err != nil || limit <= 0 {
			limit = 20
		}
		if limit > 100 {
			limit = 100
		}
	}

	items, nextCursor, hasMore := h.store.GetByConversation(id, cursor, limit)

	c.JSON(http.StatusOK, response.Success(model.MessageListPage{
		Items:      items,
		NextCursor: nextCursor,
		HasMore:    hasMore,
	}))
}

package handler

import (
	"net/http"
	"strconv"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/gin-gonic/gin"
)

// 本文件承接 Java ChatController 里「会话列表 / 历史消息 / 图片文件语音上传」这组方法。

// GetChats GET /api/chat/chats?id=
//
// 会话列表（最容易写成 N+1 的地方）：
//   - 会话本身一次查完（ListByUser）；
//   - 每个会话的最后一条消息用 conversations.last_msg_id 定位（MySQL 版是一条 JOIN）；
//   - 对端用户信息一次性批量取（ListByIDs），不在循环里查。
func (h *ChatHandler) GetChats(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	convs := h.convs.ListByUser(userID)

	// 收集私聊对端 id，批量取用户资料。
	peerIDs := make([]uint64, 0, len(convs))
	for _, conv := range convs {
		if conv.Type == model.ConversationTypeDirect {
			for _, m := range conv.Members {
				if m != userID {
					peerIDs = append(peerIDs, m)
				}
			}
		}
	}
	users, _ := h.users.ListByIDs(peerIDs)
	peerByID := make(map[uint64]model.PublicUser, len(users))
	for _, u := range users {
		peerByID[u.ID] = u.ToPublic()
	}

	sessions := make([]model.ChatSession, 0, len(convs))
	for _, conv := range convs {
		s := model.ChatSession{
			ConversationID: conv.ID,
			Type:           conv.Type,
			Title:          conv.Title,
			LastMessage:    h.store.LastByConversation(conv.ID),
			UpdatedAt:      conv.UpdatedAt,
			// Unread 阶段 2 接 Redis 计数器；现在固定 0，别在 MySQL 里 COUNT(*)。
			Unread: 0,
		}
		if conv.Type == model.ConversationTypeDirect {
			for _, m := range conv.Members {
				if m == userID {
					continue
				}
				s.PeerID = m
				if p, ok := peerByID[m]; ok {
					s.Title = p.Nickname
					if s.Title == "" {
						s.Title = p.Username
					}
					s.PeerName = p.Nickname
					s.PeerAvatar = p.Avatar
				}
			}
		}
		sessions = append(sessions, s)
	}

	c.JSON(http.StatusOK, response.Success(sessions))
}

// GetChatHistory GET /api/chat/chats/chatHistory?id=&cursor=&limit=
//
// 这就是 sql/02_explain_offset_vs_cursor.sql 里 C 段查询的正式落地：
//
//	WHERE conversation_id = ? AND id < ?
//	ORDER BY create_time DESC, id DESC LIMIT ?
//
// 权限：先校验 conversation 成员身份，再查消息 —— 顺序反了就是越权读别人的聊天记录。
func (h *ChatHandler) GetChatHistory(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var q model.ChatHistoryQuery
	if err := c.ShouldBindQuery(&q); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid query: "+err.Error(), http.StatusBadRequest))
		return
	}
	if q.ConversationID == 0 {
		// 兼容前端可能传 conversation_id。
		if id, ok := parseUintQuery(c, "conversation_id"); ok && id != 0 {
			q.ConversationID = id
		}
	}
	if q.ConversationID == 0 {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("id (conversation_id) is required", http.StatusBadRequest))
		return
	}
	if !h.convs.IsMember(q.ConversationID, userID) {
		// 不区分"会话不存在"和"你不是成员"，防会话 id 枚举。
		c.JSON(http.StatusNotFound, response.ErrorWithCode("conversation not found", http.StatusNotFound))
		return
	}

	var cursor uint64
	if q.Cursor != "" {
		v, err := strconv.ParseUint(q.Cursor, 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid cursor", http.StatusBadRequest))
			return
		}
		cursor = v
	}

	items, nextCursor, hasMore := h.store.GetByConversation(q.ConversationID, cursor, q.Limit)
	if items == nil {
		items = []*model.Message{}
	}

	next := ""
	if hasMore {
		next = strconv.FormatUint(nextCursor, 10)
	}
	c.JSON(http.StatusOK, response.Success(gin.H{
		"items":       items,
		"next_cursor": next,
		"has_more":    hasMore,
	}))
}

// SavePictureMsg POST /api/chat/chats/savePictureMsg
//
// 两步操作（存文件 + 写消息）天然不是原子的：
// 文件存成功但写库失败会留下孤儿文件，靠定时清理兜底（这里注释说明取舍）。
func (h *ChatHandler) SavePictureMsg(c *gin.Context) {
	h.saveMedia(c, 2, true)
}

// SaveFileMsg POST /api/chat/chats/saveFileMsg
func (h *ChatHandler) SaveFileMsg(c *gin.Context) {
	h.saveMedia(c, 3, false)
}

// UploadVoice POST /api/chat/chats/uploadVoice
func (h *ChatHandler) UploadVoice(c *gin.Context) {
	h.saveMedia(c, 4, false)
}

// saveMedia 是图片/文件/语音的公共实现。
//
// msg_type 约定：1 文本 / 2 图片 / 3 文件 / 4 语音（与 sql/01 的注释一致）。
func (h *ChatHandler) saveMedia(c *gin.Context, msgType int, imagesOnly bool) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	convID, ok := parseUintQuery(c, "conversation_id")
	if !ok {
		return
	}
	if convID == 0 {
		id, ok := parseUintQuery(c, "id")
		if !ok {
			return
		}
		convID = id
	}
	if convID == 0 {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("conversation_id is required", http.StatusBadRequest))
		return
	}
	if !h.convs.IsMember(convID, userID) {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("conversation not found", http.StatusNotFound))
		return
	}

	url, err := saveUpload(c, "file", imagesOnly)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("upload failed: "+err.Error(), http.StatusBadRequest))
		return
	}

	msg := &model.Message{
		ConversationID: convID,
		SenderID:       userID,
		MsgType:        msgType,
		Content:        url,
		Status:         0,
	}
	if err := h.store.Create(msg); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to save message", http.StatusInternalServerError))
		return
	}
	h.convs.Touch(convID, msg.ID)

	c.JSON(http.StatusOK, response.Success(msg))
}

package handler

import (
	"net/http"
	"strconv"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// GroupHandler 对应 Java 的 GroupChatController（@RequestMapping("/api/group")）。
//
// 路径注意：Java 类上是 /api/group，方法上又写 "groups" 没有前导斜杠，
// 最终生效路径是 /api/group/groups —— 前端按这个写死，不要"顺手修正"。
type GroupHandler struct {
	groups   store.GroupStore
	convs    store.ConversationStore
	messages store.MessageStore
	users    store.UserStore
	unread   store.UnreadStore
	cache    store.ConversationCache
}

// NewGroupHandler 注入依赖。
func NewGroupHandler(
	groups store.GroupStore,
	convs store.ConversationStore,
	messages store.MessageStore,
	users store.UserStore,
	unread store.UnreadStore,
	cache store.ConversationCache,
) *GroupHandler {
	return &GroupHandler{groups: groups, convs: convs, messages: messages, users: users, unread: unread, cache: cache}
}

// CreateGroup POST /api/group/groups
//
// 事务语义（MySQL 版必须在一个事务里）：
//  1. 建 conversations 行（消息路由的锚点）
//  2. 建 group 资料行
//  3. 写 group_member（群主 + 初始成员）
//
// 只建群不建成员 = 产生一个谁都进不去的"僵尸群"。
func (h *GroupHandler) CreateGroup(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.CreateGroupRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	conv := h.convs.CreateGroup(userID, req.Name, req.MemberIDs)
	group, err := h.groups.Create(&model.Group{
		ConversationID: conv.ID,
		Name:           req.Name,
		Avatar:         req.Avatar,
		Notice:         req.Notice,
		OwnerID:        userID,
	}, req.MemberIDs)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to create group", http.StatusInternalServerError))
		return
	}

	c.JSON(http.StatusOK, response.Success(group))
}

// GetGroupDetail GET /api/group/groups/:groupId
func (h *GroupHandler) GetGroupDetail(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	groupID, ok := parseUintParam(c, "groupId")
	if !ok {
		return
	}

	// 非成员看不到群信息（群资料也可能包含敏感内容）。
	if !h.groups.IsMember(groupID, userID) {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("group not found", http.StatusNotFound))
		return
	}
	group, err := h.groups.Get(groupID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("group not found", http.StatusNotFound))
		return
	}
	c.JSON(http.StatusOK, response.Success(group))
}

// UpdateGroup PUT /api/group/groups/:groupId
//
// 只有群主能改，且只允许改 name/avatar/notice 三个字段（白名单）。
// 直接把请求里的 map 塞进 UPDATE 是典型的 mass assignment 漏洞。
func (h *GroupHandler) UpdateGroup(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	groupID, ok := parseUintParam(c, "groupId")
	if !ok {
		return
	}

	if h.groups.Role(groupID, userID) != model.GroupRoleOwner {
		c.JSON(http.StatusForbidden, response.ErrorWithCode("only the group owner can update the group", http.StatusForbidden))
		return
	}

	var req model.UpdateGroupRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	group, err := h.groups.Update(groupID, req)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("group not found", http.StatusNotFound))
		return
	}
	// 群名/头像/公告变了，成员列表页要立刻看到新标题。
	invalidateConversationCache(h.convs, h.cache, group.ConversationID)
	c.JSON(http.StatusOK, response.Success(group))
}

// DeleteGroup DELETE /api/group/groups/:groupId
//
// 群主解散群：删群资料、删会话、成员关系。消息记录先保留（审计/回溯），
// 真要物理删除应走独立的归档任务。
func (h *GroupHandler) DeleteGroup(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	groupID, ok := parseUintParam(c, "groupId")
	if !ok {
		return
	}

	group, err := h.groups.Get(groupID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("group not found", http.StatusNotFound))
		return
	}
	if group.OwnerID != userID {
		c.JSON(http.StatusForbidden, response.ErrorWithCode("only the group owner can delete the group", http.StatusForbidden))
		return
	}

	if err := h.groups.Delete(groupID); err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("group not found", http.StatusNotFound))
		return
	}
	// 解散前先失效成员缓存（删完会话就取不到成员列表了）。
	invalidateConversationCache(h.convs, h.cache, group.ConversationID)
	h.convs.Delete(group.ConversationID)

	c.JSON(http.StatusOK, response.SuccessNoData("group deleted"))
}

// GetUserGroups GET /api/group/users/:userId/groups
//
// 只允许查询自己：路径里的 userId 不可信，看到人全量群列表本身就是越权。
func (h *GroupHandler) GetUserGroups(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	targetID, ok := parseUintParam(c, "userId")
	if !ok {
		return
	}
	if targetID != userID {
		c.JSON(http.StatusForbidden, response.ErrorWithCode("cannot list another user's groups", http.StatusForbidden))
		return
	}

	c.JSON(http.StatusOK, response.Success(h.groups.ListByUser(userID)))
}

// InviteMembers POST /api/group/groups/:groupId/members
//
// 群成员都能邀请（Java 行为），已在群里的人由存储层去重跳过。
func (h *GroupHandler) InviteMembers(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	groupID, ok := parseUintParam(c, "groupId")
	if !ok {
		return
	}
	if !h.groups.IsMember(groupID, userID) {
		c.JSON(http.StatusForbidden, response.ErrorWithCode("not a group member", http.StatusForbidden))
		return
	}

	var req model.InviteMembersRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}
	if len(req.UserIDs) > 200 {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("too many members in one request", http.StatusBadRequest))
		return
	}

	added := h.groups.AddMembers(groupID, req.UserIDs)
	memberCount := 0
	if group, err := h.groups.Get(groupID); err == nil {
		h.convs.AddMembers(group.ConversationID, req.UserIDs)
		memberCount = group.MemberCount
		// 新成员要看到这个群，老成员的成员数也变了。
		invalidateConversationCache(h.convs, h.cache, group.ConversationID)
	}

	c.JSON(http.StatusOK, response.Success(gin.H{"added": added, "member_count": memberCount}))
}

// RemoveMember DELETE /api/group/groups/:groupId/members/:userId
//
// 规则：
//   - 自己退群：允许；
//   - 群主/管理员踢人：允许；
//   - 普通成员踢别人：403；
//   - 群主不能被踢（必须先转让）。
func (h *GroupHandler) RemoveMember(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	groupID, ok := parseUintParam(c, "groupId")
	if !ok {
		return
	}
	targetID, ok := parseUintParam(c, "userId")
	if !ok {
		return
	}

	role := h.groups.Role(groupID, userID)
	if role < 0 {
		c.JSON(http.StatusForbidden, response.ErrorWithCode("not a group member", http.StatusForbidden))
		return
	}
	if targetID != userID && role < model.GroupRoleAdmin {
		c.JSON(http.StatusForbidden, response.ErrorWithCode("no permission to remove this member", http.StatusForbidden))
		return
	}
	if h.groups.Role(groupID, targetID) == model.GroupRoleOwner {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("the owner cannot be removed; transfer ownership first", http.StatusBadRequest))
		return
	}

	h.groups.RemoveMember(groupID, targetID)
	if group, err := h.groups.Get(groupID); err == nil {
		h.convs.RemoveMember(group.ConversationID, targetID)
		// 剩下的人要更新成员数；被移除的人缓存里还留着这个群，必须单独失效。
		invalidateConversationCache(h.convs, h.cache, group.ConversationID)
		_ = h.cache.Invalidate(targetID)
	}

	c.JSON(http.StatusOK, response.SuccessNoData("member removed"))
}

// GetGroupMessages GET /api/group/groups/:groupId/messages?cursor=&limit=
func (h *GroupHandler) GetGroupMessages(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}
	groupID, ok := parseUintParam(c, "groupId")
	if !ok {
		return
	}
	if !h.groups.IsMember(groupID, userID) {
		c.JSON(http.StatusForbidden, response.ErrorWithCode("not a group member", http.StatusForbidden))
		return
	}

	group, err := h.groups.Get(groupID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("group not found", http.StatusNotFound))
		return
	}

	var cursor uint64
	if raw := c.Query("cursor"); raw != "" {
		v, err := strconv.ParseUint(raw, 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid cursor", http.StatusBadRequest))
			return
		}
		cursor = v
	}

	limit := 20
	if raw := c.Query("limit"); raw != "" {
		if v, err := strconv.Atoi(raw); err == nil && v > 0 {
			limit = v
		}
	}

	items, nextCursor, hasMore := h.messages.GetByConversation(group.ConversationID, cursor, limit)
	if items == nil {
		items = []*model.Message{}
	}
	next := ""
	if hasMore {
		next = strconv.FormatUint(nextCursor, 10)
	}
	c.JSON(http.StatusOK, response.Success(model.GroupMessagesPage{Items: items, NextCursor: next}))
}

// SendGroupImage POST /api/group/messages/image
func (h *GroupHandler) SendGroupImage(c *gin.Context) { h.sendGroupMedia(c, 2, true) }

// SendGroupFile POST /api/group/messages/file
func (h *GroupHandler) SendGroupFile(c *gin.Context) { h.sendGroupMedia(c, 3, false) }

// SendGroupText POST /api/group/messages/text
//
// 前端已有这个调用但 Java 版没有对应方法（当时走 WebSocket）。
// 阶段 1 用 HTTP 落库实现，阶段 3 换 WebSocket Hub 后作为兜底保留。
func (h *GroupHandler) SendGroupText(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req struct {
		GroupID uint64 `json:"group_id" binding:"required"`
		Content string `json:"content" binding:"required,max=4000"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	group, err := h.groups.Get(req.GroupID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("group not found", http.StatusNotFound))
		return
	}
	if !h.groups.IsMember(req.GroupID, userID) {
		c.JSON(http.StatusForbidden, response.ErrorWithCode("not a group member", http.StatusForbidden))
		return
	}

	msg := &model.Message{
		ConversationID: group.ConversationID,
		SenderID:       userID,
		MsgType:        1,
		Content:        req.Content,
		Status:         0,
	}
	if err := h.messages.Create(msg); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to save message", http.StatusInternalServerError))
		return
	}
	h.convs.Touch(group.ConversationID, msg.ID)
	markMessageSent(h.convs, h.unread, h.cache, group.ConversationID, userID)
	c.JSON(http.StatusOK, response.Success(msg))
}

// sendGroupMedia 上传群图片/文件。
func (h *GroupHandler) sendGroupMedia(c *gin.Context, msgType int, imagesOnly bool) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	groupID, ok := parseUintQuery(c, "group_id")
	if !ok {
		return
	}
	if groupID == 0 {
		if id, ok := parseUintQuery(c, "id"); ok {
			groupID = id
		}
	}
	if groupID == 0 {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("group_id is required", http.StatusBadRequest))
		return
	}

	group, err := h.groups.Get(groupID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("group not found", http.StatusNotFound))
		return
	}
	if !h.groups.IsMember(groupID, userID) {
		c.JSON(http.StatusForbidden, response.ErrorWithCode("not a group member", http.StatusForbidden))
		return
	}

	url, err := saveUpload(c, "file", imagesOnly)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("upload failed: "+err.Error(), http.StatusBadRequest))
		return
	}

	msg := &model.Message{
		ConversationID: group.ConversationID,
		SenderID:       userID,
		MsgType:        msgType,
		Content:        url,
		Status:         0,
	}
	if err := h.messages.Create(msg); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to save message", http.StatusInternalServerError))
		return
	}
	h.convs.Touch(group.ConversationID, msg.ID)
	markMessageSent(h.convs, h.unread, h.cache, group.ConversationID, userID)
	c.JSON(http.StatusOK, response.Success(msg))
}

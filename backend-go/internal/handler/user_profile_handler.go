package handler

import (
	"errors"
	"net/http"
	"strings"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
)

// 本文件承接 UserController 里除"注册/登录/按 id 查/用户名校验/搜索"之外的方法。
// 全部挂在 *UserHandler 上（同包的另一个文件定义方法，Go 允许按功能拆分）。

// verifyCodeRequest 是发送/校验验证码的请求体。
type verifyCodeRequest struct {
	Target string `json:"target" binding:"required"` // 邮箱或手机号
	Code   string `json:"code,omitempty"`            // 校验时才需要
}

// SendVerifyCode POST /api/user/verifyCode/send
//
// 开发期把验证码直接放进响应体（方便 curl 联调）；
// 上线前必须删掉 dev_code 字段，改为真实邮件/短信发送 ——
// 否则这个接口等于把验证码公开给任何人，注册/找回密码的防线直接失效。
func (h *UserHandler) SendVerifyCode(c *gin.Context) {
	var req verifyCodeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	code, ttl, err := h.verify.Send(strings.TrimSpace(req.Target))
	if errors.Is(err, store.ErrVerifyTooFrequent) {
		c.JSON(http.StatusTooManyRequests, response.ErrorWithCode("发送过于频繁，请 60 秒后再试", http.StatusTooManyRequests))
		return
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to send code", http.StatusInternalServerError))
		return
	}

	c.JSON(http.StatusOK, response.SuccessMsg("code sent", gin.H{
		"target":     req.Target,
		"expires_in": int(ttl.Seconds()),
		"dev_code":   code, // TODO(生产): 删除这一行
	}))
}

// ValidateVerifyCode POST /api/user/verifyCode/validate
//
// 校验成功即消费验证码（一次性），过期/错误次数过多都会作废。
func (h *UserHandler) ValidateVerifyCode(c *gin.Context) {
	var req verifyCodeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	if !h.verify.Verify(strings.TrimSpace(req.Target), strings.TrimSpace(req.Code)) {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("验证码错误或已过期", http.StatusBadRequest))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("code verified", gin.H{"valid": true}))
}

// GetFriendsList GET /api/user/friends?id=
//
// 只允许查自己的好友列表：路径/查询里的 id 不可信，
// 传别人的 id 一律按"无权限"处理（这里直接忽略参数，始终查当前用户）。
func (h *UserHandler) GetFriendsList(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	friendIDs, err := h.friends.ListFriends(userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to list friends", http.StatusInternalServerError))
		return
	}
	users, _ := h.store.ListByIDs(friendIDs)

	out := make([]model.PublicUser, 0, len(users))
	for _, u := range users {
		out = append(out, u.ToPublic())
	}
	c.JSON(http.StatusOK, response.Success(out))
}

// GetFriendVerifyList GET /api/user/friendVerify?id=
//
// 返回"别人发给我的"待处理好友申请，并附带申请人资料（前端要显示头像昵称）。
func (h *UserHandler) GetFriendVerifyList(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	requests, err := h.friends.ListRequests(userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to list friend requests", http.StatusInternalServerError))
		return
	}

	// 一次批量取申请人，避免在循环里 N 次查用户（N+1）。
	ids := make([]uint64, 0, len(requests))
	for _, r := range requests {
		ids = append(ids, r.FromUserID)
	}
	users, _ := h.store.ListByIDs(ids)
	byID := make(map[uint64]model.PublicUser, len(users))
	for _, u := range users {
		byID[u.ID] = u.ToPublic()
	}

	type item struct {
		*model.FriendRequest
		FromUser *model.PublicUser `json:"from_user,omitempty"`
	}
	out := make([]item, 0, len(requests))
	for _, r := range requests {
		it := item{FriendRequest: r}
		if u, ok := byID[r.FromUserID]; ok {
			it.FromUser = &u
		}
		out = append(out, it)
	}
	c.JSON(http.StatusOK, response.Success(out))
}

// EditUser POST /api/user/user/edit
//
// PATCH 语义：只更新客户端真正传了的字段。
// 安全：昵称/头像/签名可改；用户名、密码哈希、id、status 一律不在这里改。
func (h *UserHandler) EditUser(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.EditUserRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	user, err := h.store.GetByID(userID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("user not found", http.StatusNotFound))
		return
	}
	if req.Nickname != nil {
		nick := strings.TrimSpace(*req.Nickname)
		if nick == "" || len([]rune(nick)) > 32 {
			c.JSON(http.StatusBadRequest, response.ErrorWithCode("nickname must be 1-32 characters", http.StatusBadRequest))
			return
		}
		user.Nickname = nick
	}
	if req.Avatar != nil {
		user.Avatar = *req.Avatar
	}
	if req.Signature != nil {
		if len([]rune(*req.Signature)) > 128 {
			c.JSON(http.StatusBadRequest, response.ErrorWithCode("signature too long", http.StatusBadRequest))
			return
		}
		user.Signature = *req.Signature
	}

	if err := h.store.Update(user); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to update user", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.Success(user.ToPublic()))
}

// ChangeAvatar POST /api/user/user/changeAvatar
//
// multipart 字段名同时兼容 file / avatar（Java 版用 avatar，前端可能传 file）。
func (h *UserHandler) ChangeAvatar(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	url, err := saveUpload(c, "file", true)
	if err != nil {
		url, err = saveUpload(c, "avatar", true)
	}
	if err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid image: "+err.Error(), http.StatusBadRequest))
		return
	}

	user, err := h.store.GetByID(userID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("user not found", http.StatusNotFound))
		return
	}
	user.Avatar = url
	if err := h.store.Update(user); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to save avatar", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.Success(gin.H{"avatar": url}))
}

// ChangePassword POST /api/user/user/changePassword
//
// 必须先验旧密码再写新哈希；旧 token 由客户端主动失效
// （阶段 2 上 Redis 黑名单后可在服务端强制踢下线）。
func (h *UserHandler) ChangePassword(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.ChangePasswordRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	user, err := h.store.GetByID(userID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("user not found", http.StatusNotFound))
		return
	}
	if bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.OldPassword)) != nil {
		c.JSON(http.StatusUnauthorized, response.ErrorWithCode("旧密码错误", http.StatusUnauthorized))
		return
	}

	hash, err := hashPassword(req.NewPassword)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode(err.Error(), http.StatusBadRequest))
		return
	}
	if err := h.store.UpdatePassword(userID, hash); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to update password", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.SuccessNoData("password changed"))
}

// ValidatePassword POST /api/user/password/validate
//
// 注意这是"密码是否正确"的预言机接口：生产环境必须限流 + 审计，
// 否则等于给攻击者开放一个在线爆破通道。
func (h *UserHandler) ValidatePassword(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.ValidatePasswordRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	user, err := h.store.GetByID(userID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("user not found", http.StatusNotFound))
		return
	}
	valid := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)) == nil
	c.JSON(http.StatusOK, response.Success(gin.H{"valid": valid}))
}

// AddTag POST /api/user/user/addTag
func (h *UserHandler) AddTag(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.TagRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	user, err := h.store.GetByID(userID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("user not found", http.StatusNotFound))
		return
	}

	tag := strings.TrimSpace(req.Tag)
	for _, t := range user.Tags {
		if t == tag {
			c.JSON(http.StatusOK, response.Success(gin.H{"tags": user.Tags})) // 幂等
			return
		}
	}
	if len(user.Tags) >= 10 {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("最多只能有 10 个标签", http.StatusBadRequest))
		return
	}
	user.Tags = append(user.Tags, tag)
	if err := h.store.Update(user); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to add tag", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.Success(gin.H{"tags": user.Tags}))
}

// RemoveTag POST /api/user/user/removeTag
func (h *UserHandler) RemoveTag(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	var req model.TagRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	user, err := h.store.GetByID(userID)
	if err != nil {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("user not found", http.StatusNotFound))
		return
	}

	tag := strings.TrimSpace(req.Tag)
	kept := make([]string, 0, len(user.Tags))
	for _, t := range user.Tags {
		if t != tag {
			kept = append(kept, t)
		}
	}
	user.Tags = kept
	if err := h.store.Update(user); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to remove tag", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.Success(gin.H{"tags": user.Tags}))
}

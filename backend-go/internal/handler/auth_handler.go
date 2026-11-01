package handler

import (
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
)

// tokenTTL 是登录态有效期；阶段 2 会上 Redis 支持滑动续期。
const tokenTTL = 24 * time.Hour

// AuthHandler 对应 Java 的 AuthController（@RequestMapping("/auth")）。
//
// 路径说明：Java 侧是 /auth/login，而前端 request.js 的 baseURL 是 /api，
// 实际请求会打到 /api/auth/login。router 里两个前缀都挂上。
type AuthHandler struct {
	users  store.UserStore
	tokens store.TokenStore
}

// NewAuthHandler 注入用户与会话存储。
func NewAuthHandler(users store.UserStore, tokens store.TokenStore) *AuthHandler {
	return &AuthHandler{users: users, tokens: tokens}
}

// Login 处理 POST /auth/login。
//
// 安全要点：
//  1. 用户不存在与密码错误返回**同一句**文案、同一个状态码 ——
//     否则等于给攻击者一个用户名枚举接口；
//  2. 密码比对用 bcrypt.CompareHashAndPassword（恒定时间），
//     不要自己写字符串比较；
//  3. token 用 crypto/rand 生成，登录成功才签发。
func (h *AuthHandler) Login(c *gin.Context) {
	var req model.LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	user, err := h.users.GetByUsername(strings.TrimSpace(req.Username))
	if err != nil {
		c.JSON(http.StatusUnauthorized, response.ErrorWithCode("用户名或密码错误", http.StatusUnauthorized))
		return
	}
	if bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)) != nil {
		c.JSON(http.StatusUnauthorized, response.ErrorWithCode("用户名或密码错误", http.StatusUnauthorized))
		return
	}
	if user.Status != 1 {
		c.JSON(http.StatusForbidden, response.ErrorWithCode("账号已被禁用", http.StatusForbidden))
		return
	}

	token, expiresAt, err := h.tokens.Issue(user.ID, tokenTTL)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to issue token", http.StatusInternalServerError))
		return
	}

	c.JSON(http.StatusOK, response.SuccessMsg("login successful", gin.H{
		"token":      token,
		"expires_at": expiresAt,
		"user":       user.ToPublic(),
	}))
}

// Logout 处理 POST /auth/logout。
//
// 内存 token 实现下，Revoke 会立即让 token 失效（真正的服务端登出）。
// 客户端同时应删除本地 token，避免拿着废 token 反复 401。
func (h *AuthHandler) Logout(c *gin.Context) {
	auth := c.GetHeader("Authorization")
	if strings.HasPrefix(auth, "Bearer ") {
		token := strings.TrimSpace(strings.TrimPrefix(auth, "Bearer "))
		if token != "" {
			_ = h.tokens.Revoke(token)
		}
	}
	c.JSON(http.StatusOK, response.SuccessNoData("logged out"))
}

// hashPassword 统一密码哈希入口（注册、改密码都走这里）。
func hashPassword(plain string) (string, error) {
	if len(plain) < 6 || len(plain) > 64 {
		return "", errors.New("password length must be 6-64")
	}
	b, err := bcrypt.GenerateFromPassword([]byte(plain), bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

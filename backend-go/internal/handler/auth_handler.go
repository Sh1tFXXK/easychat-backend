package handler

import (
	"github.com/gin-gonic/gin"
)

// AuthHandler 对应 Java 的 AuthController（@RequestMapping("/auth")）。
//
// 路径说明：Java 侧是 /auth/login，而前端 request.js 的 baseURL 是 /api，
// 实际请求会打到 /api/auth/login。router 里两个前缀都挂上，避免前端改造。
//
// 与 UserHandler.Login 的分工：后者是早期 Go 骨架里的 /api/user/login（临时），
// 正式的登录/登出统一走这里；等前端切完可以把 /api/user/login 下掉。
type AuthHandler struct {
	// TODO(你来实现时再加字段): 例如 jwtSecret []byte、userStore store.UserStore、sessionStore ...
}

// NewAuthHandler 创建 AuthHandler。
func NewAuthHandler() *AuthHandler {
	return &AuthHandler{}
}

// Login 处理 POST /auth/login（Java: AuthController#login）。
//
// TODO(你来实现)：
//  1. ShouldBindJSON 绑定登录体（用户名 + 密码）
//  2. 按用户名查用户，用 bcrypt.CompareHashAndPassword 校验密码
//  3. 校验通过后签发 JWT（HS256，claims 至少放 sub=userID、exp）
//  4. 失败一律 401 + 同一句文案：「用户名或密码错误」
//     —— 区分「用户不存在」和「密码错误」等于送攻击者一个用户名枚举接口
func (h *AuthHandler) Login(c *gin.Context) {
	notImplemented(c, "AuthHandler.Login")
}

// Logout 处理 POST /auth/logout（Java: AuthController#logout）。
//
// TODO(你来实现)：
//  1. JWT 是无状态的，真正的「登出」需要服务端状态：
//     把 token 的 jti（或整串 token 的哈希）写进 Redis 黑名单，
//     过期时间 = token 剩余有效期（阶段 2 上 Redis 时做）
//  2. 暂时可以只返回成功，并在注释里写清楚这是「前端删 token」式登出
func (h *AuthHandler) Logout(c *gin.Context) {
	notImplemented(c, "AuthHandler.Logout")
}

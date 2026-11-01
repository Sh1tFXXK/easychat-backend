package middleware

import (
	"strings"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// 认证上下文的 key，必须与 handler 包里的常量保持一致。
// 单独定义在这里是因为 middleware 不能反向依赖 handler（会形成循环 import）。
const CtxUserIDKey = "auth.userID"

// ResolveUser 解析 Authorization: Bearer <token>，把 userID 放进上下文。
//
// 设计要点：这是一个"尽力而为"的中间件 ——
//   - 带了合法 token：写入 userID，handler 直接信任；
//   - 没带或 token 非法：不拦请求，交给 handler 决定（业务接口自己会回 401）；
//   - 同时保留 X-User-ID 开发兜底（在 handler.currentUserID 里）。
//
// 为什么不在中间件里直接 401：健康检查、登录、注册等公开接口不该被拦。
// 需要强制登录的接口由 handler 调 requireUser 保证。
func ResolveUser(tokens store.TokenStore) gin.HandlerFunc {
	return func(c *gin.Context) {
		auth := c.GetHeader("Authorization")
		if strings.HasPrefix(auth, "Bearer ") {
			token := strings.TrimSpace(strings.TrimPrefix(auth, "Bearer "))
			if token != "" {
				if uid, err := tokens.Resolve(token); err == nil {
					c.Set(CtxUserIDKey, uid)
				}
			}
		}
		c.Next()
	}
}

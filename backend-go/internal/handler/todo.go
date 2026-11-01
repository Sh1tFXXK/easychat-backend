package handler

import (
	"net/http"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/gin-gonic/gin"
)

// notImplemented 是所有「路由已挂载、业务还没写」的 handler 的统一出口。
//
// 为什么统一返回 501 而不是 404：
//  1. 404 会让前端以为「接口不存在 / 路径写错了」，排查方向直接跑偏；
//  2. 501 Not Implemented 语义准确：路由在，逻辑没写；
//  3. 想看还剩多少活，一句 `grep -rn "notImplemented" internal/handler` 就数出来了。
//
// 实现某个 handler 时，把函数体里的 notImplemented(c, "Xxx.Yyy") 删掉即可，
// 这样 TODO 清单天然是自维护的。
func notImplemented(c *gin.Context, name string) {
	c.JSON(http.StatusNotImplemented, response.ErrorWithCode(name+": 尚未实现（TODO）", http.StatusNotImplemented))
}

// errorBody 是 response.ErrorWithCode 的短别名，避免 handler 里到处写长名字。
func errorBody(msg string, code int) response.Response {
	return response.ErrorWithCode(msg, code)
}

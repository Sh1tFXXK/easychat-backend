package handler

import (
	"github.com/gin-gonic/gin"
)

// AdminHandler 对应 Java 的 ErrorMonitorController（@RequestMapping("/admin/error-monitor")）。
//
// 安全提示（先记住，实现时必须做）：
// 这些接口能看到错误统计、熔断状态、还能触发 cleanup / 制造测试错误 ——
// 必须挂在管理员鉴权中间件后面，绝不能和普通用户接口共用无鉴权路由。
type AdminHandler struct {
	// TODO(你来实现时再加字段): metrics、circuitBreaker ...
}

// NewAdminHandler 创建 AdminHandler。
func NewAdminHandler() *AdminHandler {
	return &AdminHandler{}
}

// GetErrorStatistics GET /admin/error-monitor/statistics
// TODO: 按错误类型/时间窗口聚合；做分页或限流，别让统计接口自己成为性能瓶颈。
func (h *AdminHandler) GetErrorStatistics(c *gin.Context) {
	notImplemented(c, "AdminHandler.GetErrorStatistics")
}

// GetSystemHealth GET /admin/error-monitor/health
// TODO: 返回 DB/Redis/下游依赖的健康状态；注意超时控制，健康检查不能自己卡住。
func (h *AdminHandler) GetSystemHealth(c *gin.Context) {
	notImplemented(c, "AdminHandler.GetSystemHealth")
}

// GetCircuitBreakerStats GET /admin/error-monitor/circuit-breaker
// TODO: 阶段 4 接 Kafka/下游调用时再补，先返回当前状态即可。
func (h *AdminHandler) GetCircuitBreakerStats(c *gin.Context) {
	notImplemented(c, "AdminHandler.GetCircuitBreakerStats")
}

// ResetCircuitBreaker POST /admin/error-monitor/circuit-breaker/reset?serviceName=
// TODO: 这是"高危操作"——恢复一个仍在故障的下游会瞬间打满流量，建议加二次确认参数。
func (h *AdminHandler) ResetCircuitBreaker(c *gin.Context) {
	notImplemented(c, "AdminHandler.ResetCircuitBreaker")
}

// CleanupExpiredData POST /admin/error-monitor/cleanup
// TODO: 批量删除要分批（比如每批 1000 行）+ 加 sleep，避免长事务锁表。
func (h *AdminHandler) CleanupExpiredData(c *gin.Context) {
	notImplemented(c, "AdminHandler.CleanupExpiredData")
}

// TestError POST /admin/error-monitor/test-error?errorType=
// TODO: 只在 dev 环境注册这条路由（router 里用 gin.Mode() 判断），生产环境不能存在。
func (h *AdminHandler) TestError(c *gin.Context) {
	notImplemented(c, "AdminHandler.TestError")
}

// GetErrorHandlingConfig GET /admin/error-monitor/config
// TODO: 返回当前错误处理配置（重试次数、熔断阈值等），只读。
func (h *AdminHandler) GetErrorHandlingConfig(c *gin.Context) {
	notImplemented(c, "AdminHandler.GetErrorHandlingConfig")
}

package handler

import (
	"net/http"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// AdminHandler 对应 Java 的 ErrorMonitorController（@RequestMapping("/admin/error-monitor")）。
//
// 安全提醒：这些接口能看到错误统计、还能触发 cleanup / 制造测试错误，
// 生产环境必须挂在管理员鉴权中间件后面（当前为了联调暂时只校验登录态）。
type AdminHandler struct {
	store store.ErrorStore
}

// NewAdminHandler 注入错误监控存储。
func NewAdminHandler(s store.ErrorStore) *AdminHandler {
	return &AdminHandler{store: s}
}

// GetErrorStatistics GET /admin/error-monitor/statistics
func (h *AdminHandler) GetErrorStatistics(c *gin.Context) {
	if _, ok := requireUser(c); !ok {
		return
	}
	c.JSON(http.StatusOK, response.Success(h.store.Statistics()))
}

// GetSystemHealth GET /admin/error-monitor/health
func (h *AdminHandler) GetSystemHealth(c *gin.Context) {
	if _, ok := requireUser(c); !ok {
		return
	}
	c.JSON(http.StatusOK, response.Success(h.store.Health()))
}

// GetCircuitBreakerStats GET /admin/error-monitor/circuit-breaker
func (h *AdminHandler) GetCircuitBreakerStats(c *gin.Context) {
	if _, ok := requireUser(c); !ok {
		return
	}
	c.JSON(http.StatusOK, response.Success(h.store.CircuitBreaker()))
}

// ResetCircuitBreaker POST /admin/error-monitor/circuit-breaker/reset?serviceName=
//
// 高危操作：恢复一个仍在故障的下游会瞬间打满流量，生产环境建议加二次确认参数。
func (h *AdminHandler) ResetCircuitBreaker(c *gin.Context) {
	if _, ok := requireUser(c); !ok {
		return
	}

	service := c.Query("serviceName")
	if service == "" {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("serviceName is required", http.StatusBadRequest))
		return
	}
	if !h.store.ResetCircuitBreaker(service) {
		c.JSON(http.StatusNotFound, response.ErrorWithCode("unknown service: "+service, http.StatusNotFound))
		return
	}
	c.JSON(http.StatusOK, response.SuccessNoData("circuit breaker reset"))
}

// CleanupExpiredData POST /admin/error-monitor/cleanup
func (h *AdminHandler) CleanupExpiredData(c *gin.Context) {
	if _, ok := requireUser(c); !ok {
		return
	}
	cleaned := h.store.Cleanup()
	c.JSON(http.StatusOK, response.Success(gin.H{"cleaned": cleaned}))
}

// TestError POST /admin/error-monitor/test-error?errorType=
//
// 只在 dev 环境使用：router 里可以用 gin.Mode() 判断是否注册这条路由。
func (h *AdminHandler) TestError(c *gin.Context) {
	if _, ok := requireUser(c); !ok {
		return
	}

	kind := c.DefaultQuery("errorType", "test")
	h.store.Record(kind)
	c.JSON(http.StatusOK, response.Success(gin.H{"recorded": kind}))
}

// GetErrorHandlingConfig GET /admin/error-monitor/config
func (h *AdminHandler) GetErrorHandlingConfig(c *gin.Context) {
	if _, ok := requireUser(c); !ok {
		return
	}
	c.JSON(http.StatusOK, response.Success(gin.H{
		"retry_max":            3,
		"retry_backoff_ms":     500,
		"circuit_open_after":   10,
		"circuit_half_open_ms": 30000,
		"error_retention_days": 7,
	}))
}

package handler

import (
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// PresenceHandler 管理在线状态。
//
// HTTP 心跳只是阶段 2 的过渡方案：阶段 3 接入 WebSocket 后，
// 心跳走 ping/pong 帧，这里保留给"没连 WS 的旧客户端"兜底。
type PresenceHandler struct {
	presence store.PresenceStore
	// ttl 是服务端判定的在线有效期，必须和心跳间隔一起告诉客户端。
	ttl time.Duration
}

// NewPresenceHandler 注入在线状态存储。
func NewPresenceHandler(p store.PresenceStore, ttl time.Duration) *PresenceHandler {
	return &PresenceHandler{presence: p, ttl: ttl}
}

// Heartbeat POST /api/presence/heartbeat
//
// 客户端建议每 30 秒调一次（服务端 TTL 90 秒，允许丢 2 次心跳）。
// 返回值里带 next_heartbeat_seconds，客户端不用把魔法数字写死。
func (h *PresenceHandler) Heartbeat(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	if err := h.presence.Heartbeat(userID, h.ttl); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to update presence", http.StatusInternalServerError))
		return
	}

	c.JSON(http.StatusOK, response.Success(gin.H{
		"online":                 true,
		"ttl_seconds":            int(h.ttl.Seconds()),
		"next_heartbeat_seconds": int(h.ttl.Seconds()) / 3,
	}))
}

// Offline DELETE /api/presence/online
//
// 正常退出时主动下线（比等 TTL 过期更及时）。
// 注意：这个接口不是"必须调用"—— 客户端崩了照样会过期，这就是 TTL 兜底的意义。
func (h *PresenceHandler) Offline(c *gin.Context) {
	userID, ok := requireUser(c)
	if !ok {
		return
	}

	if err := h.presence.Offline(userID); err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to update presence", http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.Success(gin.H{"online": false}))
}

// GetStatus GET /api/presence/status?user_id=
//
// 返回单个用户的在线状态 + 最后活跃时间。
func (h *PresenceHandler) GetStatus(c *gin.Context) {
	if _, ok := requireUser(c); !ok {
		return
	}
	targetID, ok := parseUintQuery(c, "user_id")
	if !ok {
		return
	}
	if targetID == 0 {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("user_id is required", http.StatusBadRequest))
		return
	}

	lastSeen := h.presence.LastSeen(targetID)
	body := gin.H{"user_id": targetID, "online": h.presence.IsOnline(targetID)}
	if !lastSeen.IsZero() {
		body["last_seen"] = lastSeen.Format(time.RFC3339)
	}
	c.JSON(http.StatusOK, response.Success(body))
}

// BatchStatus GET /api/presence/online?user_ids=1,2,3
//
// 好友列表/群成员列表一次问完。上限 500，防止一次查十万个 id。
func (h *PresenceHandler) BatchStatus(c *gin.Context) {
	if _, ok := requireUser(c); !ok {
		return
	}

	raw := strings.TrimSpace(c.Query("user_ids"))
	if raw == "" {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("user_ids is required", http.StatusBadRequest))
		return
	}

	parts := strings.Split(raw, ",")
	if len(parts) > 500 {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("too many user_ids (max 500)", http.StatusBadRequest))
		return
	}

	ids := make([]uint64, 0, len(parts))
	for _, p := range parts {
		id, err := strconv.ParseUint(strings.TrimSpace(p), 10, 64)
		if err != nil || id == 0 {
			continue // 忽略脏值，别为格式错误让整个批量查询失败
		}
		ids = append(ids, id)
	}

	online := h.presence.BatchOnline(ids)
	type item struct {
		UserID   uint64 `json:"user_id"`
		Online   bool   `json:"online"`
		LastSeen string `json:"last_seen,omitempty"`
	}

	items := make([]item, 0, len(ids))
	for _, id := range ids {
		it := item{UserID: id, Online: online[id]}
		if !it.Online {
			if t := h.presence.LastSeen(id); !t.IsZero() {
				it.LastSeen = t.Format(time.RFC3339)
			}
		}
		items = append(items, it)
	}

	c.JSON(http.StatusOK, response.Success(items))
}

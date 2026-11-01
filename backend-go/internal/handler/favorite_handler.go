package handler

import (
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// FavoriteHandler 是 Java FavoriteController 的 Go 对应物。
//
// 分层职责（严格边界，别越界）：
//   - handler：HTTP 语义（bind、状态码、响应结构）、身份提取、参数格式校验；
//   - store：数据归属校验 + 查询/写入（越权过滤必须在 SQL/存储层完成）；
//   - 业务规则（如「不能收藏自己」）如果将来出现，放 service 层，不要塞进 handler。
type FavoriteHandler struct {
	store store.FavoriteStore
}

// NewFavoriteHandler 注入依赖。
// 参数用接口而不是具体类型，是为了 MySQL 实现可以无缝替换（见 store/favorite_store.go 注释）。
func NewFavoriteHandler(s store.FavoriteStore) *FavoriteHandler {
	return &FavoriteHandler{store: s}
}

// itemTypeWhitelist 是收藏类型白名单。
//
// 为什么用白名单而不是黑名单：item_type 会进 SQL 查询条件，
// 任意字符串虽然参数化后没有注入风险，但会产生无意义的查询和脏数据；
// 枚举收敛后，未来加类型必须显式改这里，避免「拼错也能写进去」的历史包袱。
var itemTypeWhitelist = map[string]struct{}{
	"MESSAGE": {},
	"USER":    {},
	"GROUP":   {},
	"FILE":    {},
}

// currentUserID 从请求中提取当前用户。
//
// 当前实现：读 X-User-ID 请求头 —— 这是**过渡方案，绝对不是生产方案**。
// 任何客户端都能伪造这个头，等于没有鉴权。
// 下一步要换成 JWT/会话中间件：中间件校验签名后把 userID 塞进 c.Set，
// 这里只负责取值。把这个 TODO 留着，面试被问「你怎么做鉴权」时这就是你的答题切入点。
func currentUserID(c *gin.Context) (uint64, bool) {
	raw := c.GetHeader("X-User-ID")
	if raw == "" {
		return 0, false
	}
	id, err := strconv.ParseUint(raw, 10, 64)
	if err != nil || id == 0 {
		return 0, false
	}
	return id, true
}

// AddFavorite godoc
// POST /api/favorites
//
// 处理顺序刻意固定为：身份 → 解析 → 业务校验 → 存储 → 响应。
// 顺序错了会有安全/性能问题：例如先解析再验身份，等于给未授权请求白白分配解析开销；
// 先写库再校验格式，会留下脏数据。
func (h *FavoriteHandler) AddFavorite(c *gin.Context) {
	// ① 身份：没有合法用户直接 401。
	// 状态码选 401 而不是 400：客户端/网关能区分「缺凭证」与「请求体坏了」。
	userID, ok := currentUserID(c)
	if !ok {
		c.JSON(http.StatusUnauthorized, response.ErrorWithCode("missing or invalid X-User-ID", http.StatusUnauthorized))
		return
	}

	// ② 解析请求体。
	// ShouldBindJSON 在 Content-Type 不对或 JSON 非法时返回错误，
	// 这里把错误透传给客户端 —— 报错信息是调试体验的一部分，不要吞掉。
	var req model.AddFavoriteRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid request body: "+err.Error(), http.StatusBadRequest))
		return
	}

	// ③ 业务校验：item_type 白名单。
	// 用 ToUpper 做一次规范化：让 "message" 和 "MESSAGE" 都通过，
	// 否则前端大小写不一致会变成「偶发」的 400，很难查。
	itemType := strings.ToUpper(strings.TrimSpace(req.ItemType))
	if _, valid := itemTypeWhitelist[itemType]; !valid {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("item_type must be one of MESSAGE/USER/GROUP/FILE", http.StatusBadRequest))
		return
	}

	// ④ 组装实体。
	// 注意 user_id 只从身份来、绝不从请求体取 —— 这是防越权的关键：
	// 如果从 body 读 user_id，攻击者可以替别人写收藏。
	fav := &model.Favorite{
		UserID:       userID,
		ItemType:     itemType,
		Title:        strings.TrimSpace(req.Title),
		Content:      req.Content,
		OriginalData: req.OriginalData,
		CategoryID:   req.CategoryID,
		Tags:         req.Tags,
		Note:         req.Note,
		SourceChatID: req.SourceChatID,
		IsAvailable:  true, // 新建即可用；下架/失效由后续业务更新
	}

	// ⑤ 落库。
	// 存储错误一律 500 并且**不把底层错误细节直接暴露给客户端**？
	// 这里为了开发期可调试暂时返回 err.Error()；正式环境应改为通用文案 + 日志记录，
	// 否则 SQL 报错会泄露表结构。这个取舍在注释里写清楚，方便以后收紧。
	if err := h.store.Create(fav); err != nil {
		if errors.Is(err, store.ErrFavoriteInvalid) {
			c.JSON(http.StatusBadRequest, response.ErrorWithCode(err.Error(), http.StatusBadRequest))
			return
		}
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to create favorite: "+err.Error(), http.StatusInternalServerError))
		return
	}

	// ⑥ 返回创建结果。
	// 用 200 而不是 201：项目统一响应封套（response.Response）已带 success/code，
	// 前端不用区分 200/201 —— 一致性优先于 REST 纯粹性。
	c.JSON(http.StatusOK, response.Success(fav))
}

// ListFavorites godoc
// GET /api/favorites?cursor=&limit=&item_type=&category_id=
//
// 游标分页的对外契约（前端必须按这个用）：
//   - 首次请求不带 cursor；
//   - 响应里 next_cursor 非空 → 带上它请求下一页；
//   - next_cursor 为空 → 没有更多数据。
//
// 注意：这里的 cursor 是**不透明字符串**，前端不要解析它、更不要自己拼。
// 今天它是内存实现的自增 id，换成 MySQL 后可能变成 base64 的 (time,id) 元组，
// 只要契约不变，前端零改动 —— 这正是「不透明游标」的价值。
func (h *FavoriteHandler) ListFavorites(c *gin.Context) {
	userID, ok := currentUserID(c)
	if !ok {
		c.JSON(http.StatusUnauthorized, response.ErrorWithCode("missing or invalid X-User-ID", http.StatusUnauthorized))
		return
	}

	var q model.FavoriteListQuery
	// ShouldBindQuery 会解析 query 参数；失败基本只发生在 limit 不是数字这种情况。
	if err := c.ShouldBindQuery(&q); err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid query: "+err.Error(), http.StatusBadRequest))
		return
	}

	items, nextCursor, err := h.store.ListByUser(userID, q)
	if err != nil {
		// 游标非法是客户端问题 → 400；其余是服务端问题 → 500。
		if errors.Is(err, store.ErrFavoriteBadCursor) {
			c.JSON(http.StatusBadRequest, response.ErrorWithCode(err.Error(), http.StatusBadRequest))
			return
		}
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to list favorites: "+err.Error(), http.StatusInternalServerError))
		return
	}

	// 即使 items 为空也返回空数组而不是 null：
	// 前端普遍写 items.map(...)，null 会直接崩；空数组是更安全的契约。
	if items == nil {
		items = []*model.Favorite{}
	}
	c.JSON(http.StatusOK, response.Success(gin.H{
		"items":       items,
		"next_cursor": nextCursor, // 空串 = 没有下一页
	}))
}

// GetFavorite godoc
// GET /api/favorites/:id
func (h *FavoriteHandler) GetFavorite(c *gin.Context) {
	userID, ok := currentUserID(c)
	if !ok {
		c.JSON(http.StatusUnauthorized, response.ErrorWithCode("missing or invalid X-User-ID", http.StatusUnauthorized))
		return
	}

	// 路径参数是字符串，必须显式解析。
	// ParseUint 而不是 Atoi：id 是无符号的，负数/超范围都应当直接判定为非法。
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil || id == 0 {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid favorite id", http.StatusBadRequest))
		return
	}

	fav, err := h.store.GetByID(userID, id)
	if err != nil {
		// 关键安全决策：不存在和越权都返回 404。
		// 如果越权返回 403，攻击者就能通过状态码差异枚举出「哪些 id 真实存在」。
		if errors.Is(err, store.ErrFavoriteNotFound) || errors.Is(err, store.ErrFavoriteForbidden) {
			c.JSON(http.StatusNotFound, response.ErrorWithCode("favorite not found", http.StatusNotFound))
			return
		}
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to get favorite: "+err.Error(), http.StatusInternalServerError))
		return
	}
	c.JSON(http.StatusOK, response.Success(fav))
}

// DeleteFavorite godoc
// DELETE /api/favorites/:id
func (h *FavoriteHandler) DeleteFavorite(c *gin.Context) {
	userID, ok := currentUserID(c)
	if !ok {
		c.JSON(http.StatusUnauthorized, response.ErrorWithCode("missing or invalid X-User-ID", http.StatusUnauthorized))
		return
	}

	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil || id == 0 {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid favorite id", http.StatusBadRequest))
		return
	}

	if err := h.store.SoftDelete(userID, id); err != nil {
		// 越权删除同样返回 404（理由同上：不暴露 id 是否存在）。
		if errors.Is(err, store.ErrFavoriteForbidden) {
			c.JSON(http.StatusNotFound, response.ErrorWithCode("favorite not found", http.StatusNotFound))
			return
		}
		c.JSON(http.StatusInternalServerError, response.ErrorWithCode("failed to delete favorite: "+err.Error(), http.StatusInternalServerError))
		return
	}
	// 幂等语义：删除不存在/已删的收藏也返回成功。
	c.JSON(http.StatusOK, response.SuccessNoData("favorite deleted"))
}

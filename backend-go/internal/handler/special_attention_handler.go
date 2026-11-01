package handler

import (
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
)

// SpecialAttentionHandler 对应 Java 的 SpecialAttentionController。
//
// 实际路径：/api/user/special-attention（不是 /api/special-attention）
// —— 前端 index.js 和历史文档不一致，以 controller 的 @RequestMapping 为准。
//
// 和收藏模块结构完全一致：handler 只做 HTTP 语义，归属校验在 store 里。
// 你可以照着 internal/handler/favorite_handler.go 的注释逐句对照着写，
// 这两个模块就是"同一套路用两遍"，练手感最合适。
type SpecialAttentionHandler struct {
	store store.SpecialAttentionStore
}

// NewSpecialAttentionHandler 注入存储。
func NewSpecialAttentionHandler(s store.SpecialAttentionStore) *SpecialAttentionHandler {
	return &SpecialAttentionHandler{store: s}
}

// GetAttentionList GET /api/user/special-attention?cursor=&limit=
// TODO: 照抄 FavoriteHandler.ListFavorites 的骨架：
//  1. currentUserID(c) 取身份，失败 401
//  2. ShouldBindQuery 绑定 cursor/limit
//  3. store.ListByUser 返回 items + nextCursor
//  4. 响应 {items, next_cursor}；items 为空时返回空数组而不是 null
//
// 注意：列表里要带上对方用户的基本信息（昵称/头像/在线状态），
// 这就是一次 JOIN users 或一次 IN 批量查询 —— 别在循环里查。
func (h *SpecialAttentionHandler) GetAttentionList(c *gin.Context) {
	notImplemented(c, "SpecialAttentionHandler.GetAttentionList")
}

// AddAttention POST /api/user/special-attention
// TODO: 请求体含 target_user_id；要挡三种情况：
//  1. 关注自己（无意义）
//  2. 目标用户不存在
//  3. 已经关注过（store 返回 ErrAttentionDuplicate → 这里返回成功，保持幂等）
func (h *SpecialAttentionHandler) AddAttention(c *gin.Context) {
	notImplemented(c, "SpecialAttentionHandler.AddAttention")
}

// RemoveAttention DELETE /api/user/special-attention
// TODO: 参数从 query 取 target_user_id（Java 版三个 DELETE 都是 query 传参，不是路径参数）。
//       幂等：删不存在的关系也返回成功。
func (h *SpecialAttentionHandler) RemoveAttention(c *gin.Context) {
	notImplemented(c, "SpecialAttentionHandler.RemoveAttention")
}

// RemoveAttentionByUser DELETE /api/user/special-attention/by-user
// TODO: 批量取消关注，body 或 query 传 target_user_id 列表；
//       一次删除多条要校验列表长度上限（防一次传 10 万个 id）。
func (h *SpecialAttentionHandler) RemoveAttentionByUser(c *gin.Context) {
	notImplemented(c, "SpecialAttentionHandler.RemoveAttentionByUser")
}

// UpdateAttention PUT /api/user/special-attention
// TODO: 更新四个通知开关；"只传了部分字段"是常态，
//       用指针字段（*bool）区分"没传"和"传了 false"，否则会把没传的开关误关掉。
func (h *SpecialAttentionHandler) UpdateAttention(c *gin.Context) {
	notImplemented(c, "SpecialAttentionHandler.UpdateAttention")
}

// BatchOperation POST /api/user/special-attention/batch
// TODO: 批量添加/删除/更新，请求体里带 operation 字段；
//       返回 {successCount, failedCount, failedItems[]}，别只说一句 "ok"。
func (h *SpecialAttentionHandler) BatchOperation(c *gin.Context) {
	notImplemented(c, "SpecialAttentionHandler.BatchOperation")
}

// CheckAttentionStatus GET /api/user/special-attention/check?target_user_id=
// TODO: 返回 {is_attention: bool}；这是高频接口（聊天页每次打开都调），
//       记得走索引 UNIQUE(user_id, target_user_id)，后续可加 Redis 缓存。
func (h *SpecialAttentionHandler) CheckAttentionStatus(c *gin.Context) {
	notImplemented(c, "SpecialAttentionHandler.CheckAttentionStatus")
}

// GetAttentionUpdates GET /api/user/special-attention/updates
// TODO: 拉取"关注的人最近的状态变更/新消息"；游标分页 + 时间窗口，
//       别做成全量扫描（这个接口最容易随数据量爆炸）。
func (h *SpecialAttentionHandler) GetAttentionUpdates(c *gin.Context) {
	notImplemented(c, "SpecialAttentionHandler.GetAttentionUpdates")
}

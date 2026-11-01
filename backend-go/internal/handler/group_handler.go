package handler

import (
	"github.com/gin-gonic/gin"
)

// GroupHandler 对应 Java 的 GroupChatController（@RequestMapping("/api/group")）。
//
// 注意 Java 侧的路径拼接很随意：类上是 /api/group，方法上又写 "groups" 没有前导斜杠，
// 最终生效路径是 /api/group/groups —— 前端就是按这个写死的，别"顺手修正"。
type GroupHandler struct {
	// TODO(你来实现时再加字段): groupStore store.GroupStore、messageStore store.MessageStore
}

// NewGroupHandler 创建 GroupHandler。
func NewGroupHandler() *GroupHandler {
	return &GroupHandler{}
}

// CreateGroup POST /api/group/groups
// TODO: 建群 + 建群主成员记录 + 拉初始成员，三件事必须在同一个事务里；
//       只建群不建成员 = 产生一个谁都进不去的"僵尸群"。
func (h *GroupHandler) CreateGroup(c *gin.Context) { notImplemented(c, "GroupHandler.CreateGroup") }

// GetGroupDetail GET /api/group/groups/:groupId
// TODO: 先校验"请求者是不是群成员"，再返回详情 —— 非成员能看到群信息本身就是越权。
func (h *GroupHandler) GetGroupDetail(c *gin.Context) { notImplemented(c, "GroupHandler.GetGroupDetail") }

// UpdateGroup PUT /api/group/groups/:groupId
// TODO: 只有群主/管理员能改；请求体是 Map<String,String>，要显式白名单允许改的字段
//       （别把传来的 map 直接塞进 UPDATE，那是典型的 mass assignment 漏洞）。
func (h *GroupHandler) UpdateGroup(c *gin.Context) { notImplemented(c, "GroupHandler.UpdateGroup") }

// DeleteGroup DELETE /api/group/groups/:groupId
// TODO: 软删还是硬删要想清楚；成员表、消息表怎么处理？级联删除要在一个事务里完成。
func (h *GroupHandler) DeleteGroup(c *gin.Context) { notImplemented(c, "GroupHandler.DeleteGroup") }

// GetUserGroups GET /api/group/users/:userId/groups
// TODO: 别信路径里的 userId —— 要么和当前登录用户一致，要么是管理员；否则能看别人全部群列表。
func (h *GroupHandler) GetUserGroups(c *gin.Context) { notImplemented(c, "GroupHandler.GetUserGroups") }

// InviteMembers POST /api/group/groups/:groupId/members
// TODO: 请求体是 userId 数组；注意去重 + 已在群里的成员要跳过（靠唯一键兜底也行）。
func (h *GroupHandler) InviteMembers(c *gin.Context) { notImplemented(c, "GroupHandler.InviteMembers") }

// RemoveMember DELETE /api/group/groups/:groupId/members/:userId
// TODO: 谁能踢人、能不能踢自己、群主能不能被踢 —— 三条规则写清楚再动手。
func (h *GroupHandler) RemoveMember(c *gin.Context) { notImplemented(c, "GroupHandler.RemoveMember") }

// GetGroupMessages GET /api/group/groups/:groupId/messages
// TODO: 和消息列表同一套游标分页（(create_time,id) 元组），返回 {items, next_cursor}；
//       别在这里退回 limit/offset —— 群消息是最容易翻到深页的场景。
func (h *GroupHandler) GetGroupMessages(c *gin.Context) {
	notImplemented(c, "GroupHandler.GetGroupMessages")
}

// SendGroupImage POST /api/group/messages/image
// TODO: multipart 上传（字段名 file）；校验大小/类型，落盘后再写消息记录。
func (h *GroupHandler) SendGroupImage(c *gin.Context) { notImplemented(c, "GroupHandler.SendGroupImage") }

// SendGroupFile POST /api/group/messages/file
// TODO: 同图片，但要额外注意文件名清洗（防路径穿越 ../）和下载鉴权。
func (h *GroupHandler) SendGroupFile(c *gin.Context) { notImplemented(c, "GroupHandler.SendGroupFile") }

// SendGroupText POST /api/group/messages/text
// TODO: 前端 index.js 里已有调用，但 Java 版没有对应方法（估计当时走 WebSocket）。
//       现在先按 HTTP 落库实现，阶段 3 换 WebSocket Hub 后这里作为 HTTP 兜底保留。
func (h *GroupHandler) SendGroupText(c *gin.Context) { notImplemented(c, "GroupHandler.SendGroupText") }

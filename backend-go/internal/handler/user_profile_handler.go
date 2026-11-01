package handler

import (
	"github.com/gin-gonic/gin"
)

// 本文件承接 UserController 里除"注册/登录/按 id 查/用户名校验/搜索"之外的方法。
// 全部挂在 *UserHandler 上（同一包的另一个文件也能定义方法，Go 允许这样按功能拆分）。
//
// Java 对照：master:src/main/java/org/example/easychat/controller/UserController.java

// SendVerifyCode POST /api/user/verifyCode/send
// TODO: 验证码 5 分钟内有效、同一邮箱/手机号 60 秒只能发一次（Redis SETNX + TTL，阶段 2 做）。
func (h *UserHandler) SendVerifyCode(c *gin.Context) {
	notImplemented(c, "UserHandler.SendVerifyCode")
}

// ValidateVerifyCode POST /api/user/verifyCode/validate
// TODO: 校验通过后立刻删除验证码（防重放）；错误次数要限流，防暴力枚举 6 位数字。
func (h *UserHandler) ValidateVerifyCode(c *gin.Context) {
	notImplemented(c, "UserHandler.ValidateVerifyCode")
}

// GetFriendsList GET /api/user/friends?id=
// TODO: 只能查自己的好友列表（或管理员），路径/查询里的 id 不可信。
func (h *UserHandler) GetFriendsList(c *gin.Context) {
	notImplemented(c, "UserHandler.GetFriendsList")
}

// GetFriendVerifyList GET /api/user/friendVerify?id=
// TODO: 好友申请列表；注意区分"收到的申请"和"我发出的申请"。
func (h *UserHandler) GetFriendVerifyList(c *gin.Context) {
	notImplemented(c, "UserHandler.GetFriendVerifyList")
}

// EditUser POST /api/user/user/edit
// TODO: 字段白名单！昵称/签名可以改，用户名/密码哈希/id 绝不能被请求体覆盖。
func (h *UserHandler) EditUser(c *gin.Context) {
	notImplemented(c, "UserHandler.EditUser")
}

// ChangeAvatar POST /api/user/user/changeAvatar
// TODO: multipart，字段名 file（Java 是 avatar，前端已经按 file 传了？实现前先抓一次真实请求确认）。
//       校验图片真实类型（读文件头，不信 Content-Type）+ 限制大小 + 随机文件名。
func (h *UserHandler) ChangeAvatar(c *gin.Context) {
	notImplemented(c, "UserHandler.ChangeAvatar")
}

// ChangePassword POST /api/user/user/changePassword
// TODO: 必须先验证旧密码，再写新密码的 bcrypt 哈希；修改成功后让旧 token 失效（阶段 2 Redis 黑名单）。
func (h *UserHandler) ChangePassword(c *gin.Context) {
	notImplemented(c, "UserHandler.ChangePassword")
}

// ValidatePassword POST /api/user/password/validate
// TODO: 这是一个"密码是否正确"的预言机接口 —— 必须限流 + 审计日志，否则等于在线爆破入口。
func (h *UserHandler) ValidatePassword(c *gin.Context) {
	notImplemented(c, "UserHandler.ValidatePassword")
}

// AddTag POST /api/user/user/addTag
// TODO: 标签长度/数量上限；同一用户标签去重。
func (h *UserHandler) AddTag(c *gin.Context) {
	notImplemented(c, "UserHandler.AddTag")
}

// RemoveTag POST /api/user/user/removeTag
// TODO: 幂等：删不存在的标签也返回成功。
func (h *UserHandler) RemoveTag(c *gin.Context) {
	notImplemented(c, "UserHandler.RemoveTag")
}

package handler

import (
	"github.com/gin-gonic/gin"
)

// 本文件承接 Java ChatController 里「会话列表 / 历史消息 / 图片文件语音上传」这组方法。
// 挂在 *ChatHandler 上，和已有的 SendChat/GetChat 同属 /api/chat 域。
//
// Java 对照：master:src/main/java/org/example/easychat/controller/ChatController.java
// 实际路径前缀是 /api/chat，方法里又写 "chats"（无前导斜杠），最终是 /api/chat/chats。

// GetChats GET /api/chat/chats?id=
// TODO: 会话列表是典型的 N+1 重灾区：
//
//	错误做法：查出 N 个会话，再循环查每个会话的最后一条消息（N+1 次 SQL）
//	正确做法：conversations.last_msg_id 冗余字段 + IN 查询一次捞回（sql/01 里已经留了这个字段）
//
// 另外未读数阶段 2 走 Redis，不要在这里 COUNT(*)。
func (h *ChatHandler) GetChats(c *gin.Context) {
	notImplemented(c, "ChatHandler.GetChats")
}

// GetChatHistory GET /api/chat/chats/chatHistory?id=&cursor=&limit=
// TODO: 这就是 sql/02 里 C 段那条游标查询的正式落地：
//
//	WHERE conversation_id = ? AND (create_time, id) < (?, ?)
//	ORDER BY create_time DESC, id DESC LIMIT ?
//
// 注意 Java 版用的是 PageResult（页码），改成游标后响应结构要同步改，
// 前端目前还在按页码传参 —— 这是升级时的兼容点。
func (h *ChatHandler) GetChatHistory(c *gin.Context) {
	notImplemented(c, "ChatHandler.GetChatHistory")
}

// SavePictureMsg POST /api/chat/chats/savePictureMsg
// TODO: multipart 图片；先存文件拿到 URL，再落一条 msg_type=2 的消息。
//       两步不是一个原子操作：文件存成功但写库失败会留下孤儿文件，要有清理策略。
func (h *ChatHandler) SavePictureMsg(c *gin.Context) {
	notImplemented(c, "ChatHandler.SavePictureMsg")
}

// SaveFileMsg POST /api/chat/chats/saveFileMsg
// TODO: 文件名清洗（去掉 ../ 和控制字符）+ 大小限制 + 下载时的权限校验。
func (h *ChatHandler) SaveFileMsg(c *gin.Context) {
	notImplemented(c, "ChatHandler.SaveFileMsg")
}

// UploadVoice POST /api/chat/chats/uploadVoice
// TODO: 语音除了存文件，通常还要存时长（前端播放器要用），字段在 content JSON 里。
func (h *ChatHandler) UploadVoice(c *gin.Context) {
	notImplemented(c, "ChatHandler.UploadVoice")
}

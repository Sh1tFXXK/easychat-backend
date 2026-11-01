package handler

import (
	"log"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
)

// 本文件收敛"发出一条消息之后要做的副作用"。
//
// 为什么单独抽出来：单聊、群聊、图片/文件/语音一共 5 个入口，
// 每个入口都手写一遍「给成员加未读 + 清缓存」，漏一个就会出现
// "发图不涨未读"这种很难查的 bug。

// markMessageSent 在消息成功落库后调用。
//
// 两个动作：
//  1. 给除发送者外的成员未读 +1；
//  2. 让所有成员的会话列表缓存失效（最后一条消息变了）。
//
// 这里刻意不返回错误：消息已经落库，未读/缓存是"尽力而为"的旁路逻辑，
// 不能因为 Redis 抖动就把一次成功的发送变成 500。失败只记日志。
func markMessageSent(
	convs store.ConversationStore,
	unread store.UnreadStore,
	cache store.ConversationCache,
	conversationID, senderID uint64,
) {
	members := convs.Members(conversationID)
	if len(members) == 0 {
		return
	}

	if err := unread.IncrementForMembers(conversationID, members, senderID); err != nil {
		log.Printf("[warn] unread increment failed conv=%d sender=%d: %v", conversationID, senderID, err)
	}
	if err := cache.Invalidate(members...); err != nil {
		log.Printf("[warn] chat list cache invalidate failed conv=%d: %v", conversationID, err)
	}
}

// invalidateConversationCache 用于群资料/成员变动这类"列表内容变了但没新消息"的场景。
func invalidateConversationCache(
	convs store.ConversationStore,
	cache store.ConversationCache,
	conversationID uint64,
) {
	if err := cache.Invalidate(convs.Members(conversationID)...); err != nil {
		log.Printf("[warn] chat list cache invalidate failed conv=%d: %v", conversationID, err)
	}
}

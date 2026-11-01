package store

import (
	"errors"
	"sort"
	"sync"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
)

// 会话相关错误。
var (
	ErrConversationNotFound  = errors.New("conversation not found")
	ErrNotConversationMember = errors.New("not a conversation member")
)

// ConversationStore 管理会话与成员关系。
//
// 私聊也是"2 个成员的会话"，和群聊共用一张表 —— 这样消息路由、未读、
// 游标分页都只写一套逻辑（sql/01_core_tables.sql 的注释里解释过）。
type ConversationStore interface {
	// EnsureDirect 返回两个用户之间的私聊会话，没有则创建。
	EnsureDirect(userA, userB uint64) *model.Conversation
	// CreateGroup 创建一个群聊会话，owner 自动成为成员。
	CreateGroup(ownerID uint64, title string, members []uint64) *model.Conversation
	Get(id uint64) (*model.Conversation, error)
	IsMember(id, userID uint64) bool
	Members(id uint64) []uint64
	AddMembers(id uint64, userIDs []uint64)
	RemoveMember(id, userID uint64)
	ListByUser(userID uint64) []*model.Conversation
	// Touch 记录会话的最后一条消息 id（列表页免扫消息表）。
	Touch(id, lastMsgID uint64)
	Delete(id uint64)
}

// InMemoryConversationStore 是内存实现。
type InMemoryConversationStore struct {
	mu            sync.RWMutex
	conversations map[uint64]*model.Conversation
	directIndex   map[friendPair]uint64 // (小id,大id) -> conversationID
	nextID        uint64
}

// NewInMemoryConversationStore 创建会话存储。
func NewInMemoryConversationStore() *InMemoryConversationStore {
	return &InMemoryConversationStore{
		conversations: make(map[uint64]*model.Conversation),
		directIndex:   make(map[friendPair]uint64),
		nextID:        1,
	}
}

// EnsureDirect 保证同一对用户永远只有一个私聊会话。
//
// 这是"幂等"接口：前端点两次"发消息"不能产生两个会话，
// 否则消息会被拆到两条历史里，用户看起来就像丢消息。
func (s *InMemoryConversationStore) EnsureDirect(userA, userB uint64) *model.Conversation {
	s.mu.Lock()
	defer s.mu.Unlock()

	key := pairKey(userA, userB)
	if id, ok := s.directIndex[key]; ok {
		return s.conversations[id]
	}

	now := time.Now()
	conv := &model.Conversation{
		ID:        s.nextID,
		Type:      model.ConversationTypeDirect,
		OwnerID:   userA,
		Members:   []uint64{userA, userB},
		CreatedAt: now,
		UpdatedAt: now,
	}
	s.nextID++
	s.conversations[conv.ID] = conv
	s.directIndex[key] = conv.ID
	return conv
}

// CreateGroup 创建群会话。
func (s *InMemoryConversationStore) CreateGroup(ownerID uint64, title string, members []uint64) *model.Conversation {
	s.mu.Lock()
	defer s.mu.Unlock()

	now := time.Now()
	conv := &model.Conversation{
		ID:        s.nextID,
		Type:      model.ConversationTypeGroup,
		Title:     title,
		OwnerID:   ownerID,
		Members:   dedupIDs(append([]uint64{ownerID}, members...)),
		CreatedAt: now,
		UpdatedAt: now,
	}
	s.nextID++
	s.conversations[conv.ID] = conv
	return conv
}

// Get 读取会话。
func (s *InMemoryConversationStore) Get(id uint64) (*model.Conversation, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	c, ok := s.conversations[id]
	if !ok {
		return nil, ErrConversationNotFound
	}
	return c, nil
}

// IsMember 判断用户是否属于会话 —— 消息读写的第一道权限门。
func (s *InMemoryConversationStore) IsMember(id, userID uint64) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	c, ok := s.conversations[id]
	if !ok {
		return false
	}
	for _, m := range c.Members {
		if m == userID {
			return true
		}
	}
	return false
}

// Members 返回成员 id 副本（返回内部切片会让调用方绕过锁修改状态）。
func (s *InMemoryConversationStore) Members(id uint64) []uint64 {
	s.mu.RLock()
	defer s.mu.RUnlock()
	c, ok := s.conversations[id]
	if !ok {
		return nil
	}
	out := make([]uint64, len(c.Members))
	copy(out, c.Members)
	return out
}

// AddMembers 批量加成员（去重）。
func (s *InMemoryConversationStore) AddMembers(id uint64, userIDs []uint64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	c, ok := s.conversations[id]
	if !ok {
		return
	}
	c.Members = dedupIDs(append(c.Members, userIDs...))
	c.UpdatedAt = time.Now()
}

// RemoveMember 移除成员（幂等）。
func (s *InMemoryConversationStore) RemoveMember(id, userID uint64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	c, ok := s.conversations[id]
	if !ok {
		return
	}
	kept := c.Members[:0]
	for _, m := range c.Members {
		if m != userID {
			kept = append(kept, m)
		}
	}
	c.Members = kept
	c.UpdatedAt = time.Now()
}

// ListByUser 返回用户的会话列表，按最后活跃时间倒序。
func (s *InMemoryConversationStore) ListByUser(userID uint64) []*model.Conversation {
	s.mu.RLock()
	defer s.mu.RUnlock()

	out := make([]*model.Conversation, 0)
	for _, c := range s.conversations {
		for _, m := range c.Members {
			if m == userID {
				out = append(out, c)
				break
			}
		}
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].UpdatedAt.Equal(out[j].UpdatedAt) {
			return out[i].ID > out[j].ID
		}
		return out[i].UpdatedAt.After(out[j].UpdatedAt)
	})
	return out
}

// Touch 更新最后消息 id 与活跃时间。
func (s *InMemoryConversationStore) Touch(id, lastMsgID uint64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if c, ok := s.conversations[id]; ok {
		c.LastMsgID = lastMsgID
		c.UpdatedAt = time.Now()
	}
}

// Delete 删除会话（群解散时调用）。
func (s *InMemoryConversationStore) Delete(id uint64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if c, ok := s.conversations[id]; ok {
		if c.Type == model.ConversationTypeDirect && len(c.Members) == 2 {
			delete(s.directIndex, pairKey(c.Members[0], c.Members[1]))
		}
		delete(s.conversations, id)
	}
}

// dedupIDs 去重并保持原顺序。
func dedupIDs(ids []uint64) []uint64 {
	seen := make(map[uint64]struct{}, len(ids))
	out := make([]uint64, 0, len(ids))
	for _, id := range ids {
		if id == 0 {
			continue
		}
		if _, ok := seen[id]; ok {
			continue
		}
		seen[id] = struct{}{}
		out = append(out, id)
	}
	return out
}

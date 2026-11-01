package store

import (
	"errors"
	"sort"
	"strconv"
	"sync"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
)

// ErrMessageNotFound is returned when a message does not exist.
var ErrMessageNotFound = errors.New("message not found")

// MessageStore is the persistence interface for chat messages.
type MessageStore interface {
	Create(msg *model.Message) error
	GetByID(id uint64) (*model.Message, error)
	GetByConversation(conversationID uint64, cursor uint64, limit int) ([]*model.Message, uint64, bool)
	// LastByConversation 取会话最后一条消息 —— 会话列表页专用，避免逐会话扫表。
	LastByConversation(conversationID uint64) *model.Message
	// CountByConversation 只用于内存实现下的统计/测试；MySQL 版应换成 Redis 计数器。
	CountByConversation(conversationID uint64) int
}

// InMemoryMessageStore is a thread-safe in-memory implementation used for
// phase-1 demos and tests.
type InMemoryMessageStore struct {
	mu       sync.RWMutex
	messages []*model.Message
	nextID   uint64
}

// NewInMemoryMessageStore creates a fresh in-memory message store.
func NewInMemoryMessageStore() *InMemoryMessageStore {
	return &InMemoryMessageStore{
		messages: make([]*model.Message, 0),
		nextID:   1,
	}
}

// Create 追加一条消息。
func (s *InMemoryMessageStore) Create(msg *model.Message) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if msg.CreateTime.IsZero() {
		msg.CreateTime = time.Now()
	}
	msg.ID = s.nextID
	s.nextID++
	if msg.MsgID == "" {
		msg.MsgID = generateMsgID(msg.ID, msg.CreateTime)
	}
	s.messages = append(s.messages, msg)
	return nil
}

// GetByID 按 id 查询。
func (s *InMemoryMessageStore) GetByID(id uint64) (*model.Message, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	for _, m := range s.messages {
		if m.ID == id {
			return m, nil
		}
	}
	return nil, ErrMessageNotFound
}

// LastByConversation 取最后一条（id 最大）消息。
func (s *InMemoryMessageStore) LastByConversation(conversationID uint64) *model.Message {
	s.mu.RLock()
	defer s.mu.RUnlock()

	var last *model.Message
	for _, m := range s.messages {
		if m.ConversationID != conversationID {
			continue
		}
		if last == nil || m.ID > last.ID {
			last = m
		}
	}
	return last
}

// CountByConversation 统计会话消息数。
func (s *InMemoryMessageStore) CountByConversation(conversationID uint64) int {
	s.mu.RLock()
	defer s.mu.RUnlock()
	n := 0
	for _, m := range s.messages {
		if m.ConversationID == conversationID {
			n++
		}
	}
	return n
}

// GetByConversation returns up to `limit` messages for a conversation with
// id < cursor (exclusive upper bound), ordered by id DESC (newest first).
//
// Cursor semantics:
//   - cursor == 0  → no upper bound, returns the newest `limit` messages
//   - cursor > 0   → returns messages older than cursor
func (s *InMemoryMessageStore) GetByConversation(conversationID uint64, cursor uint64, limit int) ([]*model.Message, uint64, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	if limit <= 0 {
		limit = 20
	}
	if limit > 100 {
		limit = 100
	}

	var filtered []*model.Message
	for _, m := range s.messages {
		if m.ConversationID != conversationID {
			continue
		}
		if cursor > 0 && m.ID >= cursor {
			continue
		}
		filtered = append(filtered, m)
	}

	sort.Slice(filtered, func(i, j int) bool { return filtered[i].ID > filtered[j].ID })

	hasMore := len(filtered) > limit
	if hasMore {
		filtered = filtered[:limit]
	}

	nextCursor := uint64(0)
	if len(filtered) > 0 {
		nextCursor = filtered[len(filtered)-1].ID
	}
	return filtered, nextCursor, hasMore
}

// generateMsgID 给没带业务 id 的消息补一个（MySQL 版应由客户端/雪花算法生成）。
func generateMsgID(seq uint64, t time.Time) string {
	return t.Format("20060102150405") + "-" + strconv.FormatUint(seq, 10)
}

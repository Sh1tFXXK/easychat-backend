package store

import (
	"errors"
	"sort"
	"sync"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
)

// ErrMessageNotFound is returned when a message does not exist.
var ErrMessageNotFound = errors.New("message not found")

// MessageStore is the persistence interface for chat messages.
// Kept behind an interface so the handler works without a real database.
type MessageStore interface {
	Create(msg *model.Message) error
	GetByConversation(conversationID uint64, cursor uint64, limit int) ([]*model.Message, uint64, bool)
}

// InMemoryMessageStore is a thread-safe in-memory implementation used for
// phase-1 demos and tests. Replace with a MySQL-backed store once the DB
// layer is wired (the handler is interface-based, so it's a drop-in swap).
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

func (s *InMemoryMessageStore) Create(msg *model.Message) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	msg.ID = s.nextID
	s.nextID++
	s.messages = append(s.messages, msg)
	return nil
 }

// GetByConversation returns up to `limit` messages for a conversation with
// id < cursor (exclusive upper bound), ordered by id DESC (newest first).
//
// Cursor semantics:
//   - cursor == 0  → no upper bound, returns the newest `limit` messages
//   - cursor > 0   → returns messages older than cursor
//
// next_cursor is the id of the oldest message in this page; the client sends
// it back as `cursor` on the next request to load older messages.
func (s *InMemoryMessageStore) GetByConversation(conversationID uint64, cursor uint64, limit int) ([]*model.Message, uint64, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()

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

	// Newest first (highest id first) — typical for chat "load older" pagination.
	sort.Slice(filtered, func(i, j int) bool {
		return filtered[i].ID > filtered[j].ID
	})

	hasMore := false
	if len(filtered) > limit {
		hasMore = true
		filtered = filtered[:limit]
	}

	nextCursor := uint64(0)
	if len(filtered) > 0 {
		nextCursor = filtered[len(filtered)-1].ID
	}

	return filtered, nextCursor, hasMore
}

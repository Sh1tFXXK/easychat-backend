package store

import (
	"errors"
	"sort"
	"sync"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
)

// 好友相关错误。
var (
	ErrFriendRequestNotFound = errors.New("friend request not found")
	ErrAlreadyFriends        = errors.New("already friends")
	ErrCannotFriendSelf      = errors.New("cannot add yourself as friend")
	ErrFriendRequestHandled  = errors.New("friend request already handled")
)

// FriendStore 管理好友申请与好友关系。
type FriendStore interface {
	SendRequest(from, to uint64, message string) (*model.FriendRequest, error)
	// ListRequests 返回发给 userID 的待处理申请（收件箱）。
	ListRequests(userID uint64) ([]*model.FriendRequest, error)
	// GetRequest 读取单条申请（用于校验处理人是不是接收方）。
	GetRequest(id uint64) (*model.FriendRequest, error)
	// HandleRequest 由接收方同意/拒绝；幂等性由状态机保证。
	HandleRequest(id, handlerID uint64, accept bool) error
	ListFriends(userID uint64) ([]uint64, error)
	AreFriends(a, b uint64) bool
}

type friendPair struct{ a, b uint64 }

// InMemoryFriendStore 是内存实现。
//
// 关系用无向 pair 存（总是小 id 在前），避免同一段好友关系存两份、
// 查的时候还要 OR 两个方向。
type InMemoryFriendStore struct {
	mu       sync.RWMutex
	requests map[uint64]*model.FriendRequest
	friends  map[friendPair]time.Time
	nextID   uint64
}

// NewInMemoryFriendStore 创建好友存储。
func NewInMemoryFriendStore() *InMemoryFriendStore {
	return &InMemoryFriendStore{
		requests: make(map[uint64]*model.FriendRequest),
		friends:  make(map[friendPair]time.Time),
		nextID:   1,
	}
}

func pairKey(a, b uint64) friendPair {
	if a > b {
		a, b = b, a
	}
	return friendPair{a, b}
}

// SendRequest 发送好友申请。
//
// 三种拒绝：加自己、已经是好友、已有待处理申请。
func (s *InMemoryFriendStore) SendRequest(from, to uint64, message string) (*model.FriendRequest, error) {
	if from == to {
		return nil, ErrCannotFriendSelf
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	if _, ok := s.friends[pairKey(from, to)]; ok {
		return nil, ErrAlreadyFriends
	}
	for _, r := range s.requests {
		if r.FromUserID == from && r.ToUserID == to && r.Status == model.FriendStatusPending {
			return nil, ErrFriendRequestHandled
		}
	}

	now := time.Now()
	req := &model.FriendRequest{
		ID:         s.nextID,
		FromUserID: from,
		ToUserID:   to,
		Message:    message,
		Status:     model.FriendStatusPending,
		CreatedAt:  now,
		UpdatedAt:  now,
	}
	s.nextID++
	s.requests[req.ID] = req
	return req, nil
}

// ListRequests 返回收到的待处理申请，按时间倒序。
func (s *InMemoryFriendStore) ListRequests(userID uint64) ([]*model.FriendRequest, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	out := make([]*model.FriendRequest, 0)
	for _, r := range s.requests {
		if r.ToUserID == userID && r.Status == model.FriendStatusPending {
			out = append(out, r)
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].ID > out[j].ID })
	return out, nil
}

// GetRequest 读取单条申请。
func (s *InMemoryFriendStore) GetRequest(id uint64) (*model.FriendRequest, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	r, ok := s.requests[id]
	if !ok {
		return nil, ErrFriendRequestNotFound
	}
	return r, nil
}

// HandleRequest 处理申请。
//
// 归属校验：只有接收方（ToUserID）能处理，否则任何人都能替别人同意好友申请。
func (s *InMemoryFriendStore) HandleRequest(id, handlerID uint64, accept bool) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	r, ok := s.requests[id]
	if !ok {
		return ErrFriendRequestNotFound
	}
	if r.ToUserID != handlerID {
		return ErrFriendRequestNotFound // 对外不暴露"存在但不是你的"
	}
	if r.Status != model.FriendStatusPending {
		return ErrFriendRequestHandled
	}

	r.UpdatedAt = time.Now()
	if accept {
		r.Status = model.FriendStatusAccepted
		s.friends[pairKey(r.FromUserID, r.ToUserID)] = r.UpdatedAt
	} else {
		r.Status = model.FriendStatusRejected
	}
	return nil
}

// ListFriends 返回好友 id 列表。
func (s *InMemoryFriendStore) ListFriends(userID uint64) ([]uint64, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	out := make([]uint64, 0)
	for p := range s.friends {
		if p.a == userID {
			out = append(out, p.b)
		} else if p.b == userID {
			out = append(out, p.a)
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i] < out[j] })
	return out, nil
}

// AreFriends 判断两人是否好友。
func (s *InMemoryFriendStore) AreFriends(a, b uint64) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	_, ok := s.friends[pairKey(a, b)]
	return ok
}

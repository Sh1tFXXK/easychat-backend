package store

import (
	"errors"
	"sync"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
)

// ErrUserNotFound is returned when a user does not exist.
var ErrUserNotFound = errors.New("user not found")

// ErrUserAlreadyExists is returned when a username is already taken.
var ErrUserAlreadyExists = errors.New("username already exists")

// ErrInvalidCredentials is returned when username/password do not match.
var ErrInvalidCredentials = errors.New("invalid credentials")

// UserStore is the persistence interface for users. Keeping this behind an
// interface lets the handler be tested without a real database.
type UserStore interface {
	Create(user *model.User) error
	GetByID(id uint64) (*model.User, error)
	GetByUsername(username string) (*model.User, error)
	Exists(username string) (bool, error)
	// Update 保存资料变更（昵称/头像/签名/标签）。
	Update(user *model.User) error
	// UpdatePassword 只更新密码哈希，避免"改密码顺手覆盖了昵称"这类事故。
	UpdatePassword(id uint64, hash string) error
	// ListByIDs 批量取用户，用于会话列表/好友列表避免 N+1。
	ListByIDs(ids []uint64) ([]*model.User, error)
}

// InMemoryUserStore is a thread-safe in-memory implementation used for phase 1
// demos and tests. Replace with a MySQL-backed store once the DB layer is ready.
type InMemoryUserStore struct {
	mu     sync.RWMutex
	users  map[uint64]*model.User
	byName map[string]uint64
	nextID uint64
}

// NewInMemoryUserStore creates a fresh in-memory user store.
func NewInMemoryUserStore() *InMemoryUserStore {
	return &InMemoryUserStore{
		users:  make(map[uint64]*model.User),
		byName: make(map[string]uint64),
		nextID: 1,
	}
}

// Create 新建用户。
func (s *InMemoryUserStore) Create(user *model.User) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if _, ok := s.byName[user.Username]; ok {
		return ErrUserAlreadyExists
	}
	now := time.Now()
	user.ID = s.nextID
	user.CreatedAt = now
	user.UpdatedAt = now
	if user.Tags == nil {
		user.Tags = []string{}
	}
	s.nextID++
	s.users[user.ID] = user
	s.byName[user.Username] = user.ID
	return nil
}

// GetByID 按 id 查询。
func (s *InMemoryUserStore) GetByID(id uint64) (*model.User, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	u, ok := s.users[id]
	if !ok {
		return nil, ErrUserNotFound
	}
	return u, nil
}

// GetByUsername 按用户名查询。
func (s *InMemoryUserStore) GetByUsername(username string) (*model.User, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	id, ok := s.byName[username]
	if !ok {
		return nil, ErrUserNotFound
	}
	return s.users[id], nil
}

// Exists 判断用户名是否已占用。
func (s *InMemoryUserStore) Exists(username string) (bool, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	_, ok := s.byName[username]
	return ok, nil
}

// Update 保存资料变更。
//
// 这里刻意不接受"整个 User 覆盖"之外的写法，但会保护 username 不被改名 ——
// 用户名是登录标识，改它要单独走一个"改名"流程（还要考虑会话、@ 提及等引用）。
func (s *InMemoryUserStore) Update(user *model.User) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	cur, ok := s.users[user.ID]
	if !ok {
		return ErrUserNotFound
	}
	cur.Nickname = user.Nickname
	cur.Avatar = user.Avatar
	cur.Signature = user.Signature
	if user.Tags != nil {
		cur.Tags = user.Tags
	}
	cur.UpdatedAt = time.Now()
	return nil
}

// UpdatePassword 更新密码哈希。
func (s *InMemoryUserStore) UpdatePassword(id uint64, hash string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	u, ok := s.users[id]
	if !ok {
		return ErrUserNotFound
	}
	u.PasswordHash = hash
	u.UpdatedAt = time.Now()
	return nil
}

// ListByIDs 批量查询；不存在的 id 直接跳过（调用方自己决定要不要报错）。
func (s *InMemoryUserStore) ListByIDs(ids []uint64) ([]*model.User, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	out := make([]*model.User, 0, len(ids))
	for _, id := range ids {
		if u, ok := s.users[id]; ok {
			out = append(out, u)
		}
	}
	return out, nil
}

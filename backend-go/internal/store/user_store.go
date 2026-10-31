package store

import (
	"errors"
	"sync"

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
}

// InMemoryUserStore is a thread-safe in-memory implementation used for phase 1
// demos and tests. Replace with a MySQL-backed store once the DB layer is ready.
type InMemoryUserStore struct {
	mu      sync.RWMutex
	users   map[uint64]*model.User
	byName  map[string]uint64
	nextID  uint64
}

// NewInMemoryUserStore creates a fresh in-memory user store.
func NewInMemoryUserStore() *InMemoryUserStore {
	return &InMemoryUserStore{
		users:  make(map[uint64]*model.User),
		byName: make(map[string]uint64),
		nextID: 1,
	}
}

func (s *InMemoryUserStore) Create(user *model.User) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if _, ok := s.byName[user.Username]; ok {
		return ErrUserAlreadyExists
	}
	user.ID = s.nextID
	s.nextID++
	s.users[user.ID] = user
	s.byName[user.Username] = user.ID
	return nil
}

func (s *InMemoryUserStore) GetByID(id uint64) (*model.User, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	u, ok := s.users[id]
	if !ok {
		return nil, ErrUserNotFound
	}
	return u, nil
}

func (s *InMemoryUserStore) GetByUsername(username string) (*model.User, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	id, ok := s.byName[username]
	if !ok {
		return nil, ErrUserNotFound
	}
	return s.users[id], nil
}

func (s *InMemoryUserStore) Exists(username string) (bool, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	_, ok := s.byName[username]
	return ok, nil
}
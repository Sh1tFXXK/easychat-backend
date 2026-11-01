package store

import (
	"sync"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
)

// NotificationStore 管理通知设置与模板。
type NotificationStore interface {
	GetSettings(userID uint64) *model.NotificationSettings
	UpdateSettings(userID uint64, req model.UpdateNotificationSettingsRequest) *model.NotificationSettings
	GetTemplate(userID uint64) *model.NotificationTemplate
	SetTemplate(userID uint64, req model.SetTemplateRequest) *model.NotificationTemplate
	// RetryFailed 触发失败通知重投，返回本次重投条数。
	RetryFailed() int
}

// InMemoryNotificationStore 是内存实现。
type InMemoryNotificationStore struct {
	mu        sync.RWMutex
	settings  map[uint64]*model.NotificationSettings
	templates map[uint64]*model.NotificationTemplate
	// failed 记录"投递失败"的通知条数，供 retry 使用（阶段 4 换成 Kafka 重试队列）。
	failed int
}

// NewInMemoryNotificationStore 创建通知存储。
func NewInMemoryNotificationStore() *InMemoryNotificationStore {
	return &InMemoryNotificationStore{
		settings:  make(map[uint64]*model.NotificationSettings),
		templates: make(map[uint64]*model.NotificationTemplate),
	}
}

// defaultSettings 是首次访问时返回的默认值：全开。
func defaultSettings(userID uint64) *model.NotificationSettings {
	return &model.NotificationSettings{
		UserID:    userID,
		EnableAll: true,
		Message:   true,
		Mention:   true,
		System:    true,
		Friend:    true,
		UpdatedAt: time.Now(),
	}
}

// GetSettings 读取设置，没有则返回默认值（懒创建）。
func (s *InMemoryNotificationStore) GetSettings(userID uint64) *model.NotificationSettings {
	s.mu.RLock()
	if v, ok := s.settings[userID]; ok {
		s.mu.RUnlock()
		return v
	}
	s.mu.RUnlock()

	s.mu.Lock()
	defer s.mu.Unlock()
	if v, ok := s.settings[userID]; ok {
		return v
	}
	v := defaultSettings(userID)
	s.settings[userID] = v
	return v
}

// UpdateSettings 按 PATCH 语义更新（只改传了的字段）。
func (s *InMemoryNotificationStore) UpdateSettings(userID uint64, req model.UpdateNotificationSettingsRequest) *model.NotificationSettings {
	s.mu.Lock()
	defer s.mu.Unlock()

	v, ok := s.settings[userID]
	if !ok {
		v = defaultSettings(userID)
		s.settings[userID] = v
	}
	if req.EnableAll != nil {
		v.EnableAll = *req.EnableAll
	}
	if req.Message != nil {
		v.Message = *req.Message
	}
	if req.Mention != nil {
		v.Mention = *req.Mention
	}
	if req.System != nil {
		v.System = *req.System
	}
	if req.Friend != nil {
		v.Friend = *req.Friend
	}
	if req.DoNotDisturb != nil {
		v.DoNotDisturb = *req.DoNotDisturb
	}
	v.UpdatedAt = time.Now()
	return v
}

// GetTemplate 读取模板，没有则返回空模板。
func (s *InMemoryNotificationStore) GetTemplate(userID uint64) *model.NotificationTemplate {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if v, ok := s.templates[userID]; ok {
		return v
	}
	return &model.NotificationTemplate{UserID: userID}
}

// SetTemplate 覆盖模板。
func (s *InMemoryNotificationStore) SetTemplate(userID uint64, req model.SetTemplateRequest) *model.NotificationTemplate {
	s.mu.Lock()
	defer s.mu.Unlock()

	v := &model.NotificationTemplate{
		UserID:    userID,
		Title:     req.Title,
		Body:      req.Body,
		UpdatedAt: time.Now(),
	}
	s.templates[userID] = v
	return v
}

// RetryFailed 模拟重投失败通知。
//
// 阶段 4 换成 Kafka：这里应该把消息重新投递到 topic，并返回投递条数。
func (s *InMemoryNotificationStore) RetryFailed() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	n := s.failed
	s.failed = 0
	return n
}

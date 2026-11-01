package store

import (
	"errors"
	"sort"
	"strconv"
	"sync"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
)

// 特别关心（special attention）存储层错误语义。
var (
	ErrAttentionNotFound  = errors.New("special attention not found")
	ErrAttentionDuplicate = errors.New("special attention already exists")
)

// SpecialAttentionStore 是「特别关心」的持久化接口。
//
// 和 FavoriteStore 一样，所有方法都带 userID —— 这是私有数据，
// 归属校验必须下沉到查询条件里，绝不能让 handler 先查出来再比对。
type SpecialAttentionStore interface {
	// ListByUser 游标分页列出某个用户关注的人。cursor 空 = 第一页。
	ListByUser(userID uint64, cursor string, limit int) ([]*model.SpecialAttention, string, error)
	// Get 取单条关注关系。
	Get(userID, targetUserID uint64) (*model.SpecialAttention, error)
	// Add 新增关注关系；已存在返回 ErrAttentionDuplicate（幂等由 handler 决定怎么处理）。
	Add(userID, targetUserID uint64) (*model.SpecialAttention, error)
	// UpdateSettings 更新四个通知开关。
	UpdateSettings(userID, targetUserID uint64, online, offline, message, statusChange bool) (*model.SpecialAttention, error)
	// Remove 删除单条关注关系；不存在视为成功（幂等）。
	Remove(userID, targetUserID uint64) error
	// RemoveByTargets 批量删除（对应 Java 的 DELETE /by-user）。
	RemoveByTargets(userID uint64, targetUserIDs []uint64) error
}

// attentionKey 是 (关注方, 被关注方) 二元组，天然唯一，可以直接当 map key。
type attentionKey struct {
	userID       uint64
	targetUserID uint64
}

// InMemorySpecialAttentionStore 是阶段 1 的内存实现。
//
// 换成 MySQL 时，唯一约束就是 UNIQUE KEY uk_user_target (user_id, target_user_id)，
// 游标依旧是 (create_time, id) 二元组 —— 和收藏的翻页逻辑一模一样。
type InMemorySpecialAttentionStore struct {
	mu     sync.RWMutex
	items  map[attentionKey]*model.SpecialAttention
	nextID uint64
}

// NewInMemorySpecialAttentionStore 创建内存实现。
func NewInMemorySpecialAttentionStore() *InMemorySpecialAttentionStore {
	return &InMemorySpecialAttentionStore{
		items:  make(map[attentionKey]*model.SpecialAttention),
		nextID: 1,
	}
}

// ListByUser 实现游标分页（这里用 id 当游标，规则同 FavoriteStore）。
func (s *InMemorySpecialAttentionStore) ListByUser(userID uint64, cursor string, limit int) ([]*model.SpecialAttention, string, error) {
	if limit <= 0 {
		limit = 20
	}
	if limit > 100 {
		limit = 100
	}

	var beforeID uint64
	if cursor != "" {
		id, err := strconv.ParseUint(cursor, 10, 64)
		if err != nil {
			return nil, "", ErrFavoriteBadCursor // 复用「游标非法」语义，handler 统一映射 400
		}
		beforeID = id
	}

	s.mu.RLock()
	defer s.mu.RUnlock()

	all := make([]*model.SpecialAttention, 0, len(s.items))
	for k, v := range s.items {
		if k.userID != userID {
			continue
		}
		if beforeID != 0 && v.ID >= beforeID {
			continue
		}
		all = append(all, v)
	}
	sort.Slice(all, func(i, j int) bool { return all[i].ID > all[j].ID })

	items := all
	if len(items) > limit {
		items = items[:limit]
	}
	nextCursor := ""
	if len(all) > limit {
		nextCursor = strconv.FormatUint(items[len(items)-1].ID, 10)
	}
	return items, nextCursor, nil
}

// Get 读取单条关注关系。
func (s *InMemorySpecialAttentionStore) Get(userID, targetUserID uint64) (*model.SpecialAttention, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	v, ok := s.items[attentionKey{userID, targetUserID}]
	if !ok {
		return nil, ErrAttentionNotFound
	}
	return v, nil
}

// Add 新增关注关系。
func (s *InMemorySpecialAttentionStore) Add(userID, targetUserID uint64) (*model.SpecialAttention, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	key := attentionKey{userID, targetUserID}
	if _, ok := s.items[key]; ok {
		return nil, ErrAttentionDuplicate
	}

	now := time.Now()
	v := &model.SpecialAttention{
		ID:                       s.nextID,
		UserID:                   userID,
		TargetUserID:             targetUserID,
		OnlineNotification:       true, // 默认全开：用户点了「特别关心」就是想要提醒
		OfflineNotification:      true,
		MessageNotification:      true,
		StatusChangeNotification: true,
		CreateTime:               now,
		UpdateTime:               now,
	}
	s.nextID++
	s.items[key] = v
	return v, nil
}

// UpdateSettings 更新四个通知开关。
func (s *InMemorySpecialAttentionStore) UpdateSettings(userID, targetUserID uint64, online, offline, message, statusChange bool) (*model.SpecialAttention, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	v, ok := s.items[attentionKey{userID, targetUserID}]
	if !ok {
		return nil, ErrAttentionNotFound
	}
	v.OnlineNotification = online
	v.OfflineNotification = offline
	v.MessageNotification = message
	v.StatusChangeNotification = statusChange
	v.UpdateTime = time.Now()
	return v, nil
}

// Remove 删除单条关注关系（幂等）。
func (s *InMemorySpecialAttentionStore) Remove(userID, targetUserID uint64) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	delete(s.items, attentionKey{userID, targetUserID})
	return nil
}

// RemoveByTargets 批量删除（幂等）。
func (s *InMemorySpecialAttentionStore) RemoveByTargets(userID uint64, targetUserIDs []uint64) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	for _, target := range targetUserIDs {
		delete(s.items, attentionKey{userID, target})
	}
	return nil
}

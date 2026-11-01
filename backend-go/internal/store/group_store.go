package store

import (
	"errors"
	"sort"
	"sync"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
)

// 群相关错误。
var (
	ErrGroupNotFound  = errors.New("group not found")
	ErrNotGroupMember = errors.New("not a group member")
	ErrNotGroupOwner  = errors.New("only the group owner can do this")
)

// GroupStore 管理群资料与成员。
type GroupStore interface {
	Create(g *model.Group, memberIDs []uint64) (*model.Group, error)
	Get(id uint64) (*model.Group, error)
	Update(id uint64, req model.UpdateGroupRequest) (*model.Group, error)
	Delete(id uint64) error
	ListByUser(userID uint64) []*model.Group
	IsMember(groupID, userID uint64) bool
	// Role 返回成员角色；非成员返回 -1。
	Role(groupID, userID uint64) int
	AddMembers(groupID uint64, userIDs []uint64) int
	RemoveMember(groupID, userID uint64)
	Members(groupID uint64) []*model.GroupMember
}

// InMemoryGroupStore 是内存实现。
//
// MySQL 版对应三张表：group（资料）、group_member（成员+角色）、conversations（消息路由）。
// 建群必须在一个事务里完成这三步，否则会出现"群存在但没人"的脏数据。
type InMemoryGroupStore struct {
	mu      sync.RWMutex
	groups  map[uint64]*model.Group
	members map[uint64]map[uint64]*model.GroupMember // groupID -> userID -> member
	nextID  uint64
}

// NewInMemoryGroupStore 创建群存储。
func NewInMemoryGroupStore() *InMemoryGroupStore {
	return &InMemoryGroupStore{
		groups:  make(map[uint64]*model.Group),
		members: make(map[uint64]map[uint64]*model.GroupMember),
		nextID:  1,
	}
}

// Create 创建群并把群主写进成员表。
func (s *InMemoryGroupStore) Create(g *model.Group, memberIDs []uint64) (*model.Group, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	now := time.Now()
	g.ID = s.nextID
	s.nextID++
	g.CreatedAt = now
	g.UpdatedAt = now
	s.groups[g.ID] = g
	s.members[g.ID] = make(map[uint64]*model.GroupMember)

	// 群主优先，角色不能被子序列的普通成员覆盖。
	s.members[g.ID][g.OwnerID] = &model.GroupMember{
		GroupID: g.ID, UserID: g.OwnerID, Role: model.GroupRoleOwner, JoinedAt: now,
	}
	for _, uid := range dedupIDs(memberIDs) {
		if uid == g.OwnerID {
			continue
		}
		s.members[g.ID][uid] = &model.GroupMember{
			GroupID: g.ID, UserID: uid, Role: model.GroupRoleMember, JoinedAt: now,
		}
	}
	g.MemberCount = len(s.members[g.ID])
	return g, nil
}

// Get 读取群资料。
func (s *InMemoryGroupStore) Get(id uint64) (*model.Group, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	g, ok := s.groups[id]
	if !ok {
		return nil, ErrGroupNotFound
	}
	return g, nil
}

// Update 更新群资料（PATCH 语义）。
func (s *InMemoryGroupStore) Update(id uint64, req model.UpdateGroupRequest) (*model.Group, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	g, ok := s.groups[id]
	if !ok {
		return nil, ErrGroupNotFound
	}
	if req.Name != nil {
		g.Name = *req.Name
	}
	if req.Avatar != nil {
		g.Avatar = *req.Avatar
	}
	if req.Notice != nil {
		g.Notice = *req.Notice
	}
	g.UpdatedAt = time.Now()
	return g, nil
}

// Delete 删除群及其成员关系。
func (s *InMemoryGroupStore) Delete(id uint64) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.groups[id]; !ok {
		return ErrGroupNotFound
	}
	delete(s.groups, id)
	delete(s.members, id)
	return nil
}

// ListByUser 返回用户加入的群。
func (s *InMemoryGroupStore) ListByUser(userID uint64) []*model.Group {
	s.mu.RLock()
	defer s.mu.RUnlock()

	out := make([]*model.Group, 0)
	for gid, ms := range s.members {
		if _, ok := ms[userID]; ok {
			if g, ok := s.groups[gid]; ok {
				out = append(out, g)
			}
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].ID < out[j].ID })
	return out
}

// IsMember 判断是否群成员。
func (s *InMemoryGroupStore) IsMember(groupID, userID uint64) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	_, ok := s.members[groupID][userID]
	return ok
}

// Role 返回成员角色，非成员返回 -1。
func (s *InMemoryGroupStore) Role(groupID, userID uint64) int {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if m, ok := s.members[groupID][userID]; ok {
		return m.Role
	}
	return -1
}

// AddMembers 批量加成员，返回实际新增人数（已在群里的跳过）。
func (s *InMemoryGroupStore) AddMembers(groupID uint64, userIDs []uint64) int {
	s.mu.Lock()
	defer s.mu.Unlock()

	ms, ok := s.members[groupID]
	if !ok {
		return 0
	}
	added := 0
	now := time.Now()
	for _, uid := range dedupIDs(userIDs) {
		if _, exists := ms[uid]; exists {
			continue
		}
		ms[uid] = &model.GroupMember{GroupID: groupID, UserID: uid, Role: model.GroupRoleMember, JoinedAt: now}
		added++
	}
	if g, ok := s.groups[groupID]; ok {
		g.MemberCount = len(ms)
		g.UpdatedAt = now
	}
	return added
}

// RemoveMember 移除成员（幂等）；群主不会被移除。
func (s *InMemoryGroupStore) RemoveMember(groupID, userID uint64) {
	s.mu.Lock()
	defer s.mu.Unlock()

	ms, ok := s.members[groupID]
	if !ok {
		return
	}
	if m, exists := ms[userID]; exists && m.Role == model.GroupRoleOwner {
		return // 群主不能被踢，必须先转让
	}
	delete(ms, userID)
	if g, ok := s.groups[groupID]; ok {
		g.MemberCount = len(ms)
		g.UpdatedAt = time.Now()
	}
}

// Members 返回成员列表。
func (s *InMemoryGroupStore) Members(groupID uint64) []*model.GroupMember {
	s.mu.RLock()
	defer s.mu.RUnlock()

	ms := s.members[groupID]
	out := make([]*model.GroupMember, 0, len(ms))
	for _, m := range ms {
		out = append(out, m)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].UserID < out[j].UserID })
	return out
}

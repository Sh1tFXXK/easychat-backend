package store

import (
	"errors"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
)

// 收藏存储层的错误语义，和 user_store.go 保持同一套风格。
//
// 为什么把「不存在」和「无权限」分成两个错误？
// handler 层对外的响应可以都映射成 404（不泄露资源是否存在，防枚举攻击），
// 但存储层必须能区分：权限错误是 bug 信号（越权/过滤器漏了），
// 404 是正常业务；如果混成一个错误，排查线上问题时无法定位。
var (
	ErrFavoriteNotFound  = errors.New("favorite not found")
	ErrFavoriteForbidden = errors.New("favorite does not belong to user")
	ErrFavoriteInvalid   = errors.New("invalid favorite payload")
	ErrFavoriteBadCursor = errors.New("invalid cursor")

	ErrCategoryNotFound  = errors.New("category not found")
	ErrCategoryDuplicate = errors.New("category name already exists")
	ErrTagNotFound       = errors.New("tag not found")
	ErrTagDuplicate      = errors.New("tag name already exists")

	defaultFavoriteLimit = 20
	maxFavoriteLimit     = 100
)

// FavoriteStore 是收藏的持久化接口。
//
// 为什么先定义接口再写实现？
//  1. handler 只依赖接口，今天用内存实现跑通接口契约，明天换成 MySQL 实现
//     时 handler 一行不改。
//  2. 单测可以直接塞假实现，不需要起数据库。
//
// 每个方法都带 userID 参数不是冗余：收藏是私有数据，
// 归属校验必须下沉到查询条件里，而不是在 handler 里先查出来再比对。
type FavoriteStore interface {
	Create(f *model.Favorite) error
	// ListByUser 按游标分页返回某个用户的收藏。
	ListByUser(userID uint64, q model.FavoriteListQuery) (items []*model.Favorite, nextCursor string, err error)
	GetByID(userID, id uint64) (*model.Favorite, error)
	// SoftDelete 逻辑删除，重复删除幂等。
	SoftDelete(userID, id uint64) error

	// ---- 单条更新 / 批量 / 搜索 / 统计 ----
	Update(userID, id uint64, req model.FavoriteUpdateRequest) (*model.Favorite, error)
	BatchDelete(userID uint64, ids []uint64) model.BatchOperationResult
	BatchUpdateCategory(userID uint64, ids []uint64, categoryID uint64) (model.BatchOperationResult, error)
	BatchAddTags(userID uint64, ids []uint64, tags []string) model.BatchOperationResult
	BatchRemoveTags(userID uint64, ids []uint64, tags []string) model.BatchOperationResult
	Search(userID uint64, req model.FavoriteSearchRequest) ([]*model.Favorite, string, error)
	Statistics(userID uint64) model.FavoriteStatistics
	CheckDuplicate(userID uint64, itemType string, sourceChatID uint64) bool

	// ---- 分类 ----
	CreateCategory(userID uint64, req model.CategoryRequest) (*model.FavoriteCategory, error)
	UpdateCategory(userID, id uint64, req model.CategoryRequest) (*model.FavoriteCategory, error)
	DeleteCategory(userID, id uint64) error
	ListCategories(userID uint64) []*model.FavoriteCategory
	InitDefaultCategories(userID uint64) int

	// ---- 标签 ----
	CreateTag(userID uint64, name string) (*model.FavoriteTag, error)
	DeleteTag(userID, id uint64) error
	ListTags(userID uint64) []*model.FavoriteTag
	PopularTags(userID uint64, limit int) []*model.FavoriteTag
	CleanupTags(userID uint64) int
}

// InMemoryFavoriteStore 是阶段 1 的内存实现。
//
// 注意：数据在进程重启后丢失，仅用于接口联调和单测。
// 真正的 MySQL 实现要复用 favorites 表的 (user_id, id) 索引做游标。
type InMemoryFavoriteStore struct {
	mu         sync.RWMutex
	favorites  map[uint64]*model.Favorite // id -> favorite
	categories map[uint64]*model.FavoriteCategory
	tags       map[uint64]*model.FavoriteTag
	nextID     uint64
	nextCatID  uint64
	nextTagID  uint64
}

// NewInMemoryFavoriteStore creates a fresh in-memory favorite store.
func NewInMemoryFavoriteStore() *InMemoryFavoriteStore {
	return &InMemoryFavoriteStore{
		favorites:  make(map[uint64]*model.Favorite),
		categories: make(map[uint64]*model.FavoriteCategory),
		tags:       make(map[uint64]*model.FavoriteTag),
		nextID:     1,
		nextCatID:  1,
		nextTagID:  1,
	}
}

// Create 落库一条收藏。
func (s *InMemoryFavoriteStore) Create(f *model.Favorite) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if f == nil || f.UserID == 0 || f.ItemType == "" {
		return ErrFavoriteInvalid
	}
	// 时间戳由存储层统一填充，而不是 handler：
	// ① 换 MySQL 后这里会被 INSERT ... NOW() 取代，handler 无需感知；
	// ② 避免调用方忘填导致 0001-01-01 这种脏时间进入响应。
	if f.CreateTime.IsZero() {
		f.CreateTime = time.Now()
	}
	f.UpdateTime = f.CreateTime
	f.ID = s.nextID
	s.nextID++
	s.favorites[f.ID] = f
	return nil
}

// ListByUser 实现游标分页。
//
// 游标设计（对应 sql/02_explain_offset_vs_cursor.sql 的 C 段）：
//   - 真实 SQL 里游标是 (create_time, id) 二元组，因为 create_time 精度到秒；
//   - 内存实现里 id 天然全序，这里直接用 id，但接口保留 string 形态 ——
//     换 MySQL 时无需改 handler 的编解码逻辑。
func (s *InMemoryFavoriteStore) ListByUser(userID uint64, q model.FavoriteListQuery) ([]*model.Favorite, string, error) {
	limit := normalizeLimit(q.Limit)

	var beforeID uint64
	if q.Cursor != "" {
		id, err := strconv.ParseUint(q.Cursor, 10, 64)
		if err != nil {
			return nil, "", ErrFavoriteBadCursor
		}
		beforeID = id
	}

	s.mu.RLock()
	defer s.mu.RUnlock()

	candidates := make([]*model.Favorite, 0, len(s.favorites))
	for _, f := range s.favorites {
		if !s.matchListFilter(f, userID, q, beforeID) {
			continue
		}
		candidates = append(candidates, f)
	}
	sort.Slice(candidates, func(i, j int) bool { return candidates[i].ID > candidates[j].ID })

	items := candidates
	if len(items) > limit {
		items = items[:limit]
	}
	nextCursor := ""
	if len(candidates) > limit {
		nextCursor = strconv.FormatUint(items[len(items)-1].ID, 10)
	}
	return items, nextCursor, nil
}

// matchListFilter 抽出过滤条件，保证 ListByUser / Search 两处逻辑不漂移。
func (s *InMemoryFavoriteStore) matchListFilter(f *model.Favorite, userID uint64, q model.FavoriteListQuery, beforeID uint64) bool {
	if f.UserID != userID || f.Deleted {
		return false // 最关键的一行：绝不返回别人的收藏
	}
	if beforeID != 0 && f.ID >= beforeID {
		return false
	}
	if q.ItemType != "" && f.ItemType != q.ItemType {
		return false
	}
	if q.CategoryID != 0 && f.CategoryID != q.CategoryID {
		return false
	}
	if q.Keyword != "" && !containsKeyword(f, q.Keyword) {
		return false
	}
	return true
}

// GetByID 读取单条收藏，归属校验与查询合并成一次操作。
func (s *InMemoryFavoriteStore) GetByID(userID, id uint64) (*model.Favorite, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	f, ok := s.favorites[id]
	if !ok || f.Deleted {
		return nil, ErrFavoriteNotFound
	}
	if f.UserID != userID {
		return nil, ErrFavoriteForbidden
	}
	return f, nil
}

// SoftDelete 逻辑删除（幂等）。
func (s *InMemoryFavoriteStore) SoftDelete(userID, id uint64) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	f, ok := s.favorites[id]
	if !ok {
		return nil
	}
	if f.UserID != userID {
		return ErrFavoriteForbidden
	}
	f.Deleted = true
	f.UpdateTime = time.Now()
	return nil
}

// Update 单条更新（PATCH 语义：指针为 nil 的字段不动）。
func (s *InMemoryFavoriteStore) Update(userID, id uint64, req model.FavoriteUpdateRequest) (*model.Favorite, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	f, ok := s.favorites[id]
	if !ok || f.Deleted {
		return nil, ErrFavoriteNotFound
	}
	if f.UserID != userID {
		return nil, ErrFavoriteForbidden
	}
	if req.Title != nil {
		f.Title = strings.TrimSpace(*req.Title)
	}
	if req.Content != nil {
		f.Content = *req.Content
	}
	if req.OriginalData != nil {
		f.OriginalData = *req.OriginalData
	}
	if req.Tags != nil {
		f.Tags = normalizeTags(*req.Tags)
	}
	if req.Note != nil {
		f.Note = *req.Note
	}
	if req.CategoryID != nil {
		f.CategoryID = *req.CategoryID
	}
	if req.IsAvailable != nil {
		f.IsAvailable = *req.IsAvailable
	}
	f.UpdateTime = time.Now()
	return f, nil
}

// BatchDelete 批量逻辑删除。
func (s *InMemoryFavoriteStore) BatchDelete(userID uint64, ids []uint64) model.BatchOperationResult {
	s.mu.Lock()
	defer s.mu.Unlock()

	res := model.BatchOperationResult{}
	for _, id := range ids {
		f, ok := s.favorites[id]
		if !ok || f.Deleted || f.UserID != userID {
			res.FailedCount++
			res.FailedIDs = append(res.FailedIDs, id)
			continue
		}
		f.Deleted = true
		f.UpdateTime = time.Now()
		res.SuccessCount++
	}
	return res
}

// BatchUpdateCategory 批量改分类。
func (s *InMemoryFavoriteStore) BatchUpdateCategory(userID uint64, ids []uint64, categoryID uint64) (model.BatchOperationResult, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	// 分类必须属于当前用户 —— 否则能把收藏塞进别人的分类。
	if categoryID != 0 {
		c, ok := s.categories[categoryID]
		if !ok {
			return model.BatchOperationResult{}, ErrCategoryNotFound
		}
		if c.UserID != userID {
			return model.BatchOperationResult{}, ErrCategoryNotFound
		}
	}

	res := model.BatchOperationResult{}
	for _, id := range ids {
		f, ok := s.favorites[id]
		if !ok || f.Deleted || f.UserID != userID {
			res.FailedCount++
			res.FailedIDs = append(res.FailedIDs, id)
			continue
		}
		f.CategoryID = categoryID
		f.UpdateTime = time.Now()
		res.SuccessCount++
	}
	return res, nil
}

// BatchAddTags 批量加标签。
func (s *InMemoryFavoriteStore) BatchAddTags(userID uint64, ids []uint64, tags []string) model.BatchOperationResult {
	s.mu.Lock()
	defer s.mu.Unlock()

	res := model.BatchOperationResult{}
	for _, id := range ids {
		f, ok := s.favorites[id]
		if !ok || f.Deleted || f.UserID != userID {
			res.FailedCount++
			res.FailedIDs = append(res.FailedIDs, id)
			continue
		}
		existing := splitTags(f.Tags)
		for _, t := range tags {
			t = strings.TrimSpace(t)
			if t == "" {
				continue
			}
			if !containsString(existing, t) {
				existing = append(existing, t)
			}
		}
		f.Tags = strings.Join(existing, ",")
		f.UpdateTime = time.Now()
		res.SuccessCount++
	}
	return res
}

// BatchRemoveTags 批量移除标签（幂等）。
func (s *InMemoryFavoriteStore) BatchRemoveTags(userID uint64, ids []uint64, tags []string) model.BatchOperationResult {
	s.mu.Lock()
	defer s.mu.Unlock()

	drop := make(map[string]struct{}, len(tags))
	for _, t := range tags {
		drop[strings.TrimSpace(t)] = struct{}{}
	}

	res := model.BatchOperationResult{}
	for _, id := range ids {
		f, ok := s.favorites[id]
		if !ok || f.Deleted || f.UserID != userID {
			res.FailedCount++
			res.FailedIDs = append(res.FailedIDs, id)
			continue
		}
		kept := make([]string, 0)
		for _, t := range splitTags(f.Tags) {
			if _, shouldDrop := drop[t]; !shouldDrop {
				kept = append(kept, t)
			}
		}
		f.Tags = strings.Join(kept, ",")
		f.UpdateTime = time.Now()
		res.SuccessCount++
	}
	return res
}

// Search 高级搜索。
func (s *InMemoryFavoriteStore) Search(userID uint64, req model.FavoriteSearchRequest) ([]*model.Favorite, string, error) {
	limit := normalizeLimit(req.Limit)

	var beforeID uint64
	if req.Cursor != "" {
		id, err := strconv.ParseUint(req.Cursor, 10, 64)
		if err != nil {
			return nil, "", ErrFavoriteBadCursor
		}
		beforeID = id
	}

	var start, end time.Time
	if req.StartTime != "" {
		if t, err := time.Parse(time.RFC3339, req.StartTime); err == nil {
			start = t
		}
	}
	if req.EndTime != "" {
		if t, err := time.Parse(time.RFC3339, req.EndTime); err == nil {
			end = t
		}
	}

	s.mu.RLock()
	defer s.mu.RUnlock()

	candidates := make([]*model.Favorite, 0)
	for _, f := range s.favorites {
		if f.UserID != userID || f.Deleted {
			continue
		}
		if beforeID != 0 && f.ID >= beforeID {
			continue
		}
		if req.ItemType != "" && f.ItemType != req.ItemType {
			continue
		}
		if req.CategoryID != 0 && f.CategoryID != req.CategoryID {
			continue
		}
		if req.Keyword != "" && !containsKeyword(f, req.Keyword) {
			continue
		}
		if req.Tag != "" && !containsString(splitTags(f.Tags), req.Tag) {
			continue
		}
		if !start.IsZero() && f.CreateTime.Before(start) {
			continue
		}
		if !end.IsZero() && f.CreateTime.After(end) {
			continue
		}
		candidates = append(candidates, f)
	}
	sort.Slice(candidates, func(i, j int) bool { return candidates[i].ID > candidates[j].ID })

	items := candidates
	if len(items) > limit {
		items = items[:limit]
	}
	nextCursor := ""
	if len(candidates) > limit {
		nextCursor = strconv.FormatUint(items[len(items)-1].ID, 10)
	}
	return items, nextCursor, nil
}

// Statistics 聚合统计。
func (s *InMemoryFavoriteStore) Statistics(userID uint64) model.FavoriteStatistics {
	s.mu.RLock()
	defer s.mu.RUnlock()

	stats := model.FavoriteStatistics{
		ByItemType: make(map[string]int),
		ByCategory: make([]model.CategoryStat, 0),
	}
	perCategory := make(map[uint64]int)

	for _, f := range s.favorites {
		if f.UserID != userID || f.Deleted {
			continue
		}
		stats.Total++
		stats.ByItemType[f.ItemType]++
		perCategory[f.CategoryID]++
	}

	for cid, count := range perCategory {
		name := "未分类"
		if cid != 0 {
			if c, ok := s.categories[cid]; ok {
				name = c.Name
			}
		}
		stats.ByCategory = append(stats.ByCategory, model.CategoryStat{CategoryID: cid, Name: name, Count: count})
	}
	sort.Slice(stats.ByCategory, func(i, j int) bool { return stats.ByCategory[i].Count > stats.ByCategory[j].Count })
	return stats
}

// CheckDuplicate 判断"同一来源的消息是否已收藏"。
func (s *InMemoryFavoriteStore) CheckDuplicate(userID uint64, itemType string, sourceChatID uint64) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()

	for _, f := range s.favorites {
		if f.UserID != userID || f.Deleted {
			continue
		}
		if f.ItemType == itemType && f.SourceChatID == sourceChatID {
			return true
		}
	}
	return false
}

// ---------------- 分类 ----------------

// CreateCategory 创建分类。
func (s *InMemoryFavoriteStore) CreateCategory(userID uint64, req model.CategoryRequest) (*model.FavoriteCategory, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	name := strings.TrimSpace(req.Name)
	if name == "" {
		return nil, ErrFavoriteInvalid
	}
	for _, c := range s.categories {
		if c.UserID == userID && strings.EqualFold(c.Name, name) {
			return nil, ErrCategoryDuplicate
		}
	}

	c := &model.FavoriteCategory{
		ID:         s.nextCatID,
		UserID:     userID,
		Name:       name,
		Icon:       req.Icon,
		Sort:       req.Sort,
		CreateTime: time.Now(),
	}
	s.nextCatID++
	s.categories[c.ID] = c
	return c, nil
}

// UpdateCategory 更新分类。
func (s *InMemoryFavoriteStore) UpdateCategory(userID, id uint64, req model.CategoryRequest) (*model.FavoriteCategory, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	c, ok := s.categories[id]
	if !ok || c.UserID != userID {
		return nil, ErrCategoryNotFound
	}
	name := strings.TrimSpace(req.Name)
	for _, other := range s.categories {
		if other.ID != id && other.UserID == userID && strings.EqualFold(other.Name, name) {
			return nil, ErrCategoryDuplicate
		}
	}
	c.Name = name
	c.Icon = req.Icon
	c.Sort = req.Sort
	return c, nil
}

// DeleteCategory 删除分类：分类下的收藏移到「未分类」(CategoryID=0)，不连带删数据。
func (s *InMemoryFavoriteStore) DeleteCategory(userID, id uint64) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	c, ok := s.categories[id]
	if !ok || c.UserID != userID {
		return ErrCategoryNotFound
	}
	for _, f := range s.favorites {
		if f.UserID == userID && f.CategoryID == id {
			f.CategoryID = 0
			f.UpdateTime = time.Now()
		}
	}
	delete(s.categories, id)
	return nil
}

// ListCategories 返回分类（带收藏计数）。
func (s *InMemoryFavoriteStore) ListCategories(userID uint64) []*model.FavoriteCategory {
	s.mu.RLock()
	defer s.mu.RUnlock()

	counts := make(map[uint64]int64)
	for _, f := range s.favorites {
		if f.UserID == userID && !f.Deleted {
			counts[f.CategoryID]++
		}
	}

	out := make([]*model.FavoriteCategory, 0)
	for _, c := range s.categories {
		if c.UserID != userID {
			continue
		}
		copied := *c
		copied.Count = counts[c.ID]
		out = append(out, &copied)
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Sort == out[j].Sort {
			return out[i].ID < out[j].ID
		}
		return out[i].Sort < out[j].Sort
	})
	return out
}

// InitDefaultCategories 初始化默认分类（幂等）。
func (s *InMemoryFavoriteStore) InitDefaultCategories(userID uint64) int {
	defaults := []string{"工作", "学习", "生活"}
	added := 0
	for _, name := range defaults {
		if _, err := s.CreateCategory(userID, model.CategoryRequest{Name: name}); err == nil {
			added++
		}
	}
	return added
}

// ---------------- 标签 ----------------

// CreateTag 创建标签。
func (s *InMemoryFavoriteStore) CreateTag(userID uint64, name string) (*model.FavoriteTag, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	name = strings.TrimSpace(name)
	if name == "" {
		return nil, ErrFavoriteInvalid
	}
	for _, t := range s.tags {
		if t.UserID == userID && strings.EqualFold(t.Name, name) {
			return nil, ErrTagDuplicate
		}
	}

	tag := &model.FavoriteTag{ID: s.nextTagID, UserID: userID, Name: name, CreateTime: time.Now()}
	s.nextTagID++
	s.tags[tag.ID] = tag
	return tag, nil
}

// DeleteTag 删除标签，并从所有收藏上摘掉。
func (s *InMemoryFavoriteStore) DeleteTag(userID, id uint64) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	tag, ok := s.tags[id]
	if !ok || tag.UserID != userID {
		return ErrTagNotFound
	}
	for _, f := range s.favorites {
		if f.UserID != userID || f.Deleted {
			continue
		}
		kept := make([]string, 0)
		for _, t := range splitTags(f.Tags) {
			if t != tag.Name {
				kept = append(kept, t)
			}
		}
		f.Tags = strings.Join(kept, ",")
	}
	delete(s.tags, id)
	return nil
}

// ListTags 返回标签（带使用次数）。
func (s *InMemoryFavoriteStore) ListTags(userID uint64) []*model.FavoriteTag {
	s.mu.RLock()
	defer s.mu.RUnlock()

	usage := s.tagUsageLocked(userID)
	out := make([]*model.FavoriteTag, 0)
	for _, t := range s.tags {
		if t.UserID != userID {
			continue
		}
		copied := *t
		copied.Count = usage[t.Name]
		out = append(out, &copied)
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Count == out[j].Count {
			return out[i].ID < out[j].ID
		}
		return out[i].Count > out[j].Count
	})
	return out
}

// PopularTags 返回使用次数 TopN。
func (s *InMemoryFavoriteStore) PopularTags(userID uint64, limit int) []*model.FavoriteTag {
	if limit <= 0 {
		limit = 10
	}
	if limit > 50 {
		limit = 50
	}
	all := s.ListTags(userID)
	if len(all) > limit {
		all = all[:limit]
	}
	return all
}

// CleanupTags 清理没人使用的标签，返回删除数量。
func (s *InMemoryFavoriteStore) CleanupTags(userID uint64) int {
	s.mu.Lock()
	defer s.mu.Unlock()

	usage := s.tagUsageLocked(userID)
	removed := 0
	for id, t := range s.tags {
		if t.UserID == userID && usage[t.Name] == 0 {
			delete(s.tags, id)
			removed++
		}
	}
	return removed
}

// tagUsageLocked 统计标签使用次数。调用方必须已持有读/写锁。
func (s *InMemoryFavoriteStore) tagUsageLocked(userID uint64) map[string]int64 {
	usage := make(map[string]int64)
	for _, f := range s.favorites {
		if f.UserID != userID || f.Deleted {
			continue
		}
		for _, t := range splitTags(f.Tags) {
			usage[t]++
		}
	}
	return usage
}

// ---------------- 工具函数 ----------------

func normalizeLimit(limit int) int {
	if limit <= 0 {
		return defaultFavoriteLimit
	}
	if limit > maxFavoriteLimit {
		return maxFavoriteLimit
	}
	return limit
}

func normalizeTags(raw string) string {
	return strings.Join(splitTags(raw), ",")
}

func splitTags(raw string) []string {
	if strings.TrimSpace(raw) == "" {
		return []string{}
	}
	parts := strings.Split(raw, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			out = append(out, p)
		}
	}
	return out
}

func containsString(list []string, target string) bool {
	for _, v := range list {
		if v == target {
			return true
		}
	}
	return false
}

// containsKeyword 在 title/content/note 里做大小写不敏感的包含匹配。
// MySQL 版对应 WHERE (title LIKE ? OR content LIKE ? OR note LIKE ?)，
// 注意 LIKE 的 % 和 _ 需要转义（否则用户输入 % 会匹配全部）。
func containsKeyword(f *model.Favorite, keyword string) bool {
	k := strings.ToLower(keyword)
	return strings.Contains(strings.ToLower(f.Title), k) ||
		strings.Contains(strings.ToLower(f.Content), k) ||
		strings.Contains(strings.ToLower(f.Note), k)
}

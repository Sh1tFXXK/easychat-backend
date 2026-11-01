package store

import (
	"errors"
	"sort"
	"strconv"
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
	ErrFavoriteNotFound   = errors.New("favorite not found")
	ErrFavoriteForbidden  = errors.New("favorite does not belong to user")
	ErrFavoriteInvalid    = errors.New("invalid favorite payload")
	ErrFavoriteBadCursor  = errors.New("invalid cursor")
	defaultFavoriteLimit  = 20
	maxFavoriteLimit      = 100
)

// FavoriteStore 是收藏的持久化接口。
//
// 为什么先定义接口再写实现？
//  1. handler 只依赖接口，今天用内存实现跑通接口契约，明天换成 MySQL 实现
//     时 handler 一行不改（这就是你这个项目从 Java 迁移过来的最大收益之一）。
//  2. 单测可以直接塞假实现，不需要起数据库。
//
// 每个方法都带 userID 参数不是冗余：收藏是私有数据，
// 越权读取属于安全漏洞，最稳妥的写法是让「归属校验」下沉到存储层查询条件里，
// 而不是在 handler 里拿着别人的 id 先查出来再比对（先查后比容易漏）。
type FavoriteStore interface {
	Create(f *model.Favorite) error
	// ListByUser 按游标分页返回某个用户的收藏。
	// cursor 传空字符串表示第一页；返回值 nextCursor 为空表示没有下一页。
	ListByUser(userID uint64, q model.FavoriteListQuery) (items []*model.Favorite, nextCursor string, err error)
	GetByID(userID, id uint64) (*model.Favorite, error)
	// SoftDelete 逻辑删除。重复删除应为幂等（不返回错误），
	// 因为客户端网络重试是常态，非幂等的 DELETE 会造成前端 500 噪音。
	SoftDelete(userID, id uint64) error
}

// InMemoryFavoriteStore 是阶段 1 的内存实现。
//
// 注意：它不是线程安全的替代品意义上的「生产可用」——
// 数据在进程重启后丢失，仅用于接口联调和单测。
// 真正的 MySQL 实现要复用 sql/ 里 favorites 表的 (user_id, id) 索引做游标。
type InMemoryFavoriteStore struct {
	mu        sync.RWMutex
	favorites map[uint64]*model.Favorite // id -> favorite
	nextID    uint64
}

// NewInMemoryFavoriteStore creates a fresh in-memory favorite store.
func NewInMemoryFavoriteStore() *InMemoryFavoriteStore {
	return &InMemoryFavoriteStore{
		favorites: make(map[uint64]*model.Favorite),
		nextID:    1,
	}
}

// Create 落库一条收藏。
//
// 这里做的最小校验是「存储层的最后一道门」：
// handler 已经校验过一次，但存储层可能被其它调用方（定时任务、测试）
// 直接使用，所以不能假设上游已经校验干净。
func (s *InMemoryFavoriteStore) Create(f *model.Favorite) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if f == nil || f.UserID == 0 || f.ItemType == "" {
		return ErrFavoriteInvalid
	}
	// 时间戳由存储层统一填充，而不是 handler：
	// ① 换 MySQL 后这里会被 INSERT ... NOW() 取代，handler 无需感知；
	// ② 避免调用方忘填导致 0001-01-01 这种脏时间进入响应。
	// 如果调用方显式设置了时间（数据迁移场景），则保留原值。
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
// 游标设计说明（和 SQL 侧 sql/02_explain_offset_vs_cursor.sql 的 C 段对应）：
//   - 真实 SQL 里游标是 (create_time, id) 二元组，因为 create_time 精度到秒，
//     同一秒多条记录必须靠 id 做 tie-breaker 才能稳定翻页；
//   - 内存实现里 id 全局单调递增，等价于一个天然的全序游标，
//     所以这里直接用 id 即可，但接口保留 string 形态——
//     这样换成 MySQL 时无需改 handler 的编解码逻辑
//     （handler 只需要透传 nextCursor，不关心它内部是什么）。
func (s *InMemoryFavoriteStore) ListByUser(userID uint64, q model.FavoriteListQuery) ([]*model.Favorite, string, error) {
	limit := q.Limit
	if limit <= 0 {
		limit = defaultFavoriteLimit
	}
	if limit > maxFavoriteLimit {
		limit = maxFavoriteLimit
	}

	// 游标解析：空串 = 第一页；非法游标 = 客户端问题，返回可区分的错误。
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

	// 先按过滤条件筛，再按 id 倒序（等价于「最新在前」），
	// 再根据游标截断 —— 顺序不能反：如果先截断后过滤会漏数据。
	candidates := make([]*model.Favorite, 0, len(s.favorites))
	for _, f := range s.favorites {
		if f.UserID != userID {
			continue // 最关键的一行：绝不返回别人的收藏
		}
		if q.ItemType != "" && f.ItemType != q.ItemType {
			continue
		}
		if q.CategoryID != 0 && f.CategoryID != q.CategoryID {
			continue
		}
		if beforeID != 0 && f.ID >= beforeID {
			continue // 游标之后的才有资格进入本页
		}
		candidates = append(candidates, f)
	}
	sort.Slice(candidates, func(i, j int) bool { return candidates[i].ID > candidates[j].ID })

	items := candidates
	if len(items) > limit {
		items = items[:limit]
	}

	// nextCursor 的语义：只有「还有下一页」时才给；
	// 前端拿到非空 next_cursor 就继续翻，拿到空串就停止请求。
	nextCursor := ""
	if len(candidates) > limit {
		nextCursor = strconv.FormatUint(items[len(items)-1].ID, 10)
	}
	return items, nextCursor, nil
}

// GetByID 读取单条收藏，归属校验与查询合并成一次操作。
//
// 注意这里返回 ErrFavoriteForbidden 而不是 NotFound 的取舍：
// 存储层如实报告原因，handler 统一对客户端返回 404 以不泄露存在性。
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

// SoftDelete 逻辑删除。
//
// 幂等性：记录不存在/已删除都视为成功 ——
// 客户端重试 DELETE 不应该收到 404，否则前端要额外处理这种「成功的失败」。
func (s *InMemoryFavoriteStore) SoftDelete(userID, id uint64) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	f, ok := s.favorites[id]
	if !ok {
		return nil // 幂等：目标不存在 = 已经是删除态
	}
	if f.UserID != userID {
		return ErrFavoriteForbidden
	}
	f.Deleted = true
	return nil
}

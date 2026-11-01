package store

import (
	"sync"
	"time"
)

// ErrorStore 汇总错误监控数据（对应 Java 的 ErrorMonitorController）。
//
// 真实实现应接 Prometheus/OpenTelemetry；阶段 1 用内存计数器把接口跑通，
// 重点是把返回结构定下来，前端不需要等到监控系统接完才能联调。
type ErrorStore interface {
	Record(kind string)
	Statistics() map[string]any
	Health() map[string]any
	CircuitBreaker() map[string]any
	ResetCircuitBreaker(service string) bool
	Cleanup() int
}

// InMemoryErrorStore 是内存实现。
type InMemoryErrorStore struct {
	mu        sync.Mutex
	startedAt time.Time
	counts    map[string]int
	lastError time.Time
	services  map[string]bool // service -> breaker open
	expired   int             // 待清理的过期错误记录数
}

// NewInMemoryErrorStore 创建错误监控存储。
func NewInMemoryErrorStore() *InMemoryErrorStore {
	return &InMemoryErrorStore{
		startedAt: time.Now(),
		counts:    make(map[string]int),
		services: map[string]bool{
			"mysql":  false,
			"redis":  false,
			"kafka":  false,
			"socket": false,
		},
	}
}

// Record 记录一次错误。
func (s *InMemoryErrorStore) Record(kind string) {
	if kind == "" {
		kind = "unknown"
	}
	s.mu.Lock()
	s.counts[kind]++
	s.lastError = time.Now()
	s.expired += 1
	s.mu.Unlock()
}

// Statistics 返回错误统计。
func (s *InMemoryErrorStore) Statistics() map[string]any {
	s.mu.Lock()
	defer s.mu.Unlock()

	total := 0
	byType := make(map[string]int, len(s.counts))
	for k, v := range s.counts {
		byType[k] = v
		total += v
	}
	var last string
	if !s.lastError.IsZero() {
		last = s.lastError.Format(time.RFC3339)
	}
	return map[string]any{
		"total":      total,
		"by_type":    byType,
		"last_error": last,
		"uptime_sec": int(time.Since(s.startedAt).Seconds()),
	}
}

// Health 返回依赖健康状态。
func (s *InMemoryErrorStore) Health() map[string]any {
	s.mu.Lock()
	defer s.mu.Unlock()

	deps := make(map[string]string, len(s.services))
	healthy := true
	for name, open := range s.services {
		if open {
			deps[name] = "down"
			healthy = false
		} else {
			deps[name] = "up"
		}
	}
	return map[string]any{
		"status":       map[bool]string{true: "healthy", false: "degraded"}[healthy],
		"dependencies": deps,
		"checked_at":   time.Now().Format(time.RFC3339),
	}
}

// CircuitBreaker 返回各服务熔断器状态。
func (s *InMemoryErrorStore) CircuitBreaker() map[string]any {
	s.mu.Lock()
	defer s.mu.Unlock()

	out := make(map[string]string, len(s.services))
	for name, open := range s.services {
		if open {
			out[name] = "open"
		} else {
			out[name] = "closed"
		}
	}
	return map[string]any{"breakers": out}
}

// ResetCircuitBreaker 手动合上某个服务的熔断器。
func (s *InMemoryErrorStore) ResetCircuitBreaker(service string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.services[service]; !ok {
		return false
	}
	s.services[service] = false
	return true
}

// Cleanup 清理过期错误记录，返回清理条数。
func (s *InMemoryErrorStore) Cleanup() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	n := s.expired
	s.expired = 0
	return n
}

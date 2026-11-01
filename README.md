# easychat-go 重写

Java 版完整代码在 master 分支（参考用）。本分支为 Go 重写现场：

- `sql/` —— 核心 schema + EXPLAIN 实验（阶段 1）
- `backend-go/` —— Go 后端（阶段 1 全接口 + 阶段 2 Redis）
- `docs/evidence/` —— 实跑证据（EXPLAIN、Redis 冒烟）

## 运行

```bash
# 只跑内存实现（默认，无需任何外部依赖）
cd backend-go && go run .        # :8080

# 带 Redis（阶段 2）
docker compose up -d redis
cd backend-go && REDIS_ADDR=127.0.0.1:6379 go run .

# 全部基础设施（MySQL + Redis）
docker compose up -d
```

| 文档 | 内容 |
|---|---|
| [`backend-go/docs/ROUTES.md`](backend-go/docs/ROUTES.md) | 全部路由（89 条，自动生成） |
| [`docs/redis.md`](docs/redis.md) | Redis key 设计、TTL、缓存粒度取舍 |
| [`docs/evidence/02_explain_result.txt`](docs/evidence/02_explain_result.txt) | 阶段 1 EXPLAIN / EXPLAIN ANALYZE 原始输出 |
| [`docs/evidence/redis_smoke.txt`](docs/evidence/redis_smoke.txt) | 阶段 2 Redis 冒烟（23/23） |

## 路线

1. [x] 阶段1 SQL + 游标分页（EXPLAIN 见 `sql/`）
2. [x] 阶段1 Go 接口：会话/消息/收藏/特别关心/群聊/用户/通知/监控
3. [x] 阶段2 Redis：登录态、验证码、未读计数、在线状态、会话列表缓存
4. [ ] 阶段3 WebSocket 网关（Hub 模式 + 心跳）
5. [ ] 阶段4 Kafka 落库解耦

## 阶段 2 实现说明

- `REDIS_ADDR` 未配置时自动用内存实现；配置了连不上默认降级并告警，
  `REDIS_REQUIRED=true` 则启动即退出（生产建议）
- 未读：`unread:<userID>` hash，`HGETALL` 一次拿全，发送者不加未读
- 在线：`presence:online:<userID>` + 90s TTL 心跳续期（进程崩了自动过期）
- 会话列表：`cache:chatlist:<userID>` 只缓存骨架，未读实时叠加；
  发消息时失效所有成员缓存
- 未读/缓存写失败不影响消息发送（旁路逻辑只记日志）
- Go 侧依赖：`github.com/redis/go-redis/v9`，测试用 `miniredis`

## 阶段 1 实现说明

- 所有 `store` 都是接口 + 内存实现，换 MySQL/Redis 时 handler 与 router 无需改动
- 鉴权：`POST /auth/login` 签发不透明 token；请求带 `Authorization: Bearer <token>`
  （开发期兼容 `X-User-ID`，**上生产前删掉**）
- 收藏的游标是不透明字符串，前端只透传 `next_cursor`，不解析
- 越权与资源不存在统一返回 404，避免 id 枚举
- 上传文件落在 `backend-go/uploads/`，通过 `/uploads/<name>` 访问（已在 .gitignore）

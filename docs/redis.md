# 阶段 2 · Redis：会话、未读、在线状态、会话列表缓存

对应简历 bullet 4：

> 使用 Redis 实现用户会话管理与消息未读计数，缓存在线状态、最近会话列表等热点数据，降低数据库压力。

## 1. 先看 key 设计

| key | 类型 | value | TTL | 用途 |
|---|---|---|---|---|
| `auth:token:<token>` | string | userID | 24h | 登录态（会话管理） |
| `verify:code:<target>` | string | 6 位验证码 | 5m | 邮箱/手机验证码 |
| `verify:cooldown:<target>` | string | 1 | 60s | 发送冷却（`SET NX` 原子占位） |
| `verify:attempts:<target>` | string | 失败次数 | 5m | 连错 5 次作废验证码 |
| `unread:<userID>` | hash | `{conversationID: count}` | 30d | 未读计数 |
| `presence:online:<userID>` | string | 1 | 90s | 在线状态（心跳续期） |
| `presence:lastseen:<userID>` | string | Unix 秒 | 7d | 最后活跃时间 |
| `cache:chatlist:<userID>` | string | 会话列表 JSON | 60s | 会话列表骨架缓存 |

前缀常量集中在 `backend-go/internal/store/redis_client.go`，改前缀只改一处。

## 2. 三个关键取舍（面试重点）

### 2.1 未读为什么用 hash，不用 `unread:<conv>:<user>` 独立 key

会话列表页要一次拿「我这个人的所有会话未读」：

- hash：`HGETALL unread:<userID>`，**1 次往返**；
- 独立 key：`MGET unread:1:1 unread:2:1 ...`，N 个 key、N 个网络包，还要自己拼 key。

已读清零也从「删一个 key」变成 `HDEL` 一个 field。

代价是单 hash 随会话数增长，用 30 天 TTL 兜底回收；用户维度会话数天然有上界。

### 2.2 在线状态为什么用 TTL，不用布尔值

`presence:online:<userID> = 1, EX 90`：

- 客户端每 30s 心跳续期；
- 进程被 `kill -9`、手机断网时，服务端没机会执行「下线」，key 会自己过期；
- 显式 `DEL`（正常退出）只是让状态更及时，不是唯一保障。

如果只用「上线 SET 1 / 下线 DEL」，一定会出现永远在线的幽灵用户。

### 2.3 会话列表缓存为什么不缓存未读数

缓存的是「骨架」：标题、成员、最后一条消息（变化慢）。
未读数每条消息都变，塞进缓存就得每次发消息失效所有人的列表。

所以：

- 缓存里 `unread` 恒为 0；
- `GetChats` 拿到骨架后，用 `unread:<userID>` 的 `HGETALL` **实时叠加**；
- 用户点已读时只改 Redis 计数，**不需要失效缓存**。

发消息时做两件事（`handler/message_fanout.go`）：

1. 给除发送者外的成员未读 +1；
2. 失效所有成员的 `cache:chatlist:<userID>`（最后一条消息变了）。

## 3. 降级与一致性

- `REDIS_ADDR` 未配置 → 自动用内存实现，`go run .` 直接能跑（本地开发友好）。
- 配置了但连不上：
  - 默认：打 warn 降级到内存；
  - `REDIS_REQUIRED=true`：启动即退出（生产建议）。
- 未读/缓存写失败**不影响发消息**：消息已落库，旁路逻辑失败只记日志。
  不能因为 Redis 抖动把一次成功的发送变成 500。

已知边界（写清楚比藏着好）：

- `conversations/messages/groups` 仍在内存 store（阶段 3 才落 MySQL）；
- 多实例部署时，内存版会话/群数据不共享 —— 这是阶段 3 的任务，不是 Redis 能解决的；
- 未读计数是「允许少量误差」的统计值，极端并发下可能少 1，不做分布式事务。

## 4. 怎么跑

```bash
# 1) 起 Redis（二选一）
docker compose up -d redis
# 或本机 redis-server --port 6379

# 2) 带 Redis 启动后端
cd backend-go
REDIS_ADDR=127.0.0.1:6379 go run .

# 3) 不配 Redis 也能跑（内存实现）
go run .
```

环境变量：

| 变量 | 默认 | 说明 |
|---|---|---|
| `REDIS_ADDR` | 空（用内存） | `host:port` |
| `REDIS_PASSWORD` | 空 | |
| `REDIS_DB` | 0 | |
| `REDIS_REQUIRED` | 空 | `true` = 连不上就退出 |

## 5. 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/chat/unread` | 未读汇总 `{total, items[]}`（App 角标） |
| POST | `/api/chat/chats/read` | 单会话已读 `{conversation_id}` |
| POST | `/api/chat/chats/readAll` | 全部已读 |
| POST | `/api/presence/heartbeat` | 心跳，返回 `ttl_seconds` / `next_heartbeat_seconds` |
| POST | `/api/presence/offline` | 主动下线（DELETE `/api/presence/online` 等价） |
| GET | `/api/presence/status?user_id=` | 单人在线状态 + 最后活跃 |
| GET | `/api/presence/online?user_ids=1,2,3` | 批量在线状态（好友/群成员列表） |

## 6. 测试与证据

```bash
cd backend-go
go test ./internal/store -run 'TestRedis' -v   # miniredis，不需要真 Redis
```

- `internal/store/redis_stores_test.go`：token 过期/吊销、验证码冷却/重放/5 次锁定、
  未读跳过发送者、在线状态 TTL 过期、缓存 TTL 与失效。
- 真实 Redis 7.4.2 冒烟：`docs/evidence/redis_smoke.txt`（23/23 通过，
  另跑阶段 1 全量回归 70/70）。

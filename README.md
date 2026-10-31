# easychat-go 重写

Java 版完整代码在 master 分支（参考用）。本分支为 Go 重写现场：

- `sql/` —— 核心 schema（阶段1产出，已完成）
- `backend-go/` —— Go 代码目录（我来写，等你来review）

## 路线
1. [x] 阶段1 SQL + 游标分页（EXPLAIN 见 sql/）
2. [ ] 阶段1 Go接口：/api/messages?cursor= 游标分页接口
3. [ ] 阶段2 Redis 会话/未读
4. [ ] 阶段3 WebSocket 网关（Hub模式 + 心跳）
5. [ ] 阶段4 Kafka 落库解耦

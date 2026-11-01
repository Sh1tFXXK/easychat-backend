# easychat-go 重写

Java 版完整代码在 master 分支（参考用）。本分支为 Go 重写现场：

- `sql/` —— 核心 schema + EXPLAIN 实验（阶段 1）
- `backend-go/` —— Go 后端（阶段 1，全部接口已实现，内存 store）

## 运行

```bash
cd backend-go
go run .          # :8080
```

完整路由清单见 [`backend-go/docs/ROUTES.md`](backend-go/docs/ROUTES.md)（81 条）；
阶段 1 的 EXPLAIN 实跑输出见 [`docs/evidence/02_explain_result.txt`](docs/evidence/02_explain_result.txt)。

## 路线

1. [x] 阶段1 SQL + 游标分页（EXPLAIN 见 `sql/`）
2. [x] 阶段1 Go 接口：会话/消息/收藏/特别关心/群聊/用户/通知/监控全部实现
3. [ ] 阶段2 Redis：会话、未读计数、验证码、token 黑名单
4. [ ] 阶段3 WebSocket 网关（Hub 模式 + 心跳）
5. [ ] 阶段4 Kafka 落库解耦

## 阶段 1 实现说明

- 所有 `store` 都是接口 + 内存实现，换 MySQL/Redis 时 handler 与 router 无需改动
- 鉴权：`POST /auth/login` 签发不透明 token；请求带 `Authorization: Bearer <token>`
  （开发期兼容 `X-User-ID`，**上生产前删掉**）
- 收藏的游标是不透明字符串，前端只透传 `next_cursor`，不解析
- 越权与资源不存在统一返回 404，避免 id 枚举
- 上传文件落在 `backend-go/uploads/`，通过 `/uploads/<name>` 访问（已在 .gitignore）

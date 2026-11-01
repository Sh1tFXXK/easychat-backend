# easychat-sql · 阶段 1：表结构 + 游标分页

对应 Go 分支：`go/rewrite`（项目根 `backend-go/`）。

## 文件

| 文件 | 内容 |
|---|---|
| `01_core_tables.sql` | 核心四张表：users / conversations / conversation_members / chat_messages |
| `02_explain_offset_vs_cursor.sql` | 造 5 万条数据 + offset vs cursor 的 `EXPLAIN` / `EXPLAIN ANALYZE` 对照 + 删索引对照 + 思考题 |
| `../docs/evidence/02_explain_result.txt` | 本机实跑输出（MySQL 8.0.46），面试/复盘直接翻这个 |

## 一、先起 MySQL（Docker）

```bash
docker run -d --name easychat-mysql \
  -e MYSQL_ROOT_PASSWORD=dev123 \
  -e MYSQL_DATABASE=easychat \
  -p 3306:3306 mysql:8.0

# 灌入表结构（路径按你自己机器改）
docker exec -i easychat-mysql mysql -uroot -pdev123 easychat \
  < sql/01_core_tables.sql
```

## 二、跑 EXPLAIN 实验

```bash
docker exec -i easychat-mysql mysql -uroot -pdev123 easychat \
  < sql/02_explain_offset_vs_cursor.sql
```

`02` 会先 `TRUNCATE chat_messages`（本地实验库专用），再灌 5 万条，然后依次输出：

- **B 段**：`LIMIT 4000, 20` 深偏移（`EXPLAIN` + `EXPLAIN ANALYZE`）
- **C 段**：`create_time < 锚点 LIMIT 20` 游标分页（`EXPLAIN` + `EXPLAIN ANALYZE`）
- **D 段**：`DROP INDEX` 后再跑一次，验证 `type=ALL → type=range`，最后把索引加回来

## 三、你要交的作业（发出来我 review）

1. **B 段**的 `type`、`rows`，以及 `EXPLAIN ANALYZE` 里的 `actual rows`
2. **C 段**同样的三项
3. **你自己的解释**：为什么实际扫描从 4020 行掉到 20 行？`rows` 和 `actual rows` 为什么不一样？
4. 02 文件末尾 5 道思考题的答案

## 学习锚点（写作业前扫一眼）

- **组合索引 = 电话簿**：`(user_id, create_time)` 是先按 user_id 分册、册内按时间排序；只给 create_time 不翻不开书
- **offset 的无知**：`LIMIT 4000, 20` 引擎不知道你要哪 20 条，必须按顺序产出 4020 行再丢掉前 4000
- **游标的聪明**：锚点直接告诉引擎"从这条往前数 20 条"，扫描量 ≈ 一页
- **`ALL → range` 的前提**：D2 段说明得很清楚 —— 没有索引时，游标也还是 `ALL`
- **为什么锚点带 id**：`create_time` 精度到秒，同一秒可能多条，`(create_time, id)` 才是全序

## 后续阶段

阶段 2 Redis 未读/在线状态 → 阶段 3 WebSocket Hub 网关 → 阶段 4 Kafka 落库解耦。

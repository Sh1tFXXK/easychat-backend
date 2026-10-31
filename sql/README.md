# easychat-sql · Go 重写第一阶段（你上手 + 我 review）

路径：`~/omarchy/easychat-sql/`
分支任务对应：`~/fileDemo/easychat-backend` 的 `go/rewrite` 分支

## 文件

| 文件 | 内容 |
|---|---|
| `01_core_tables.sql` | 核心四张表：users / conversations / conversation_members / chat_messages |
| `02_explain_offset_vs_cursor.sql` | 造 5 万条测试数据 + offset vs cursor 两条 EXPLAIN + 思考题 |

## 一、先起 MySQL（推荐 Docker，别脏本机）

```bash
docker run -d --name easychat-mysql \
  -e MYSQL_ROOT_PASSWORD=dev123 \
  -e MYSQL_DATABASE=easychat \
  -p 3306:3306 mysql:8.0

# 灌入表结构
docker exec -i easychat-mysql mysql -uroot -pdev123 easychat \
  < /home/wcomarchy/omarchy/easychat-sql/01_core_tables.sql
```

## 二、跑 EXPLAIN 实验

```bash
docker exec -i easychat-mysql mysql -uroot -pdev123 easychat \
  < /home/wcomarchy/omarchy/easychat-sql/02_explain_offset_vs_cursor.sql
```

## 三、你要交的作业（发出来我 review）

1. **B 段（offset）的 EXPLAIN 输出** —— 重点贴 `type` 和 `rows` 两列
2. **C 段（cursor）的 EXPLAIN 输出** —— 对照同一两列
3. **自己的解释**：为什么 rows 从 4 万级掉到 20 级？一句话、蒙的没关系，我听了给反馈
4. 02 文件末尾的三道思考题答案

## 学习锚点（写作业前扫一眼）

- **组合索引 = 电话簿排序规则**：`(user_id, create_time)` 是按"先按 user_id 分册、每册内按时间排序"组卷的，user_id 不给就开不了书
- **offset 的无知**：`LIMIT 40000, 20` 引擎不知道你要哪 20 条，它得顺着 B+ 树把前 40000 条路由出来再丢掉
- **游标的聪明**：锚点直接告诉引擎"从这页之后开始吐"，产出的范围 ≈ 一页大小
- **为什么锚点带 id**：create_time 粒度到秒，同一秒内多条消息顺序不明 → 元组才能当书签

## 后续阶段（此阶段只到 SQL 层）

阶段 2 Redis 未读/在线状态 → 阶段 3 WebSocket Hub 网关 → 阶段 4 Kafka 落库解耦
（教学节奏不变：SQL 你跑、EXPLAIN 你发、我点评；Go 代码任务下个阶段再布置）

-- ============================================================
-- 游标分页 vs 偏移分页 —— EXPLAIN 对比实验
-- 简历 bullet：LIMIT offset 深分页 → 基于上一页末条 id 的游标分页，
--              通过 EXPLAIN 验证 type=ALL → type=range
-- 运行方法见 README.md
-- ============================================================

USE easychat;

-- ------------------------------------------------------------
-- A. 先造一批数据（不加任何提升，仅让扫描代价可见）
--    5 万条消息分布在 10 个用户上
-- ------------------------------------------------------------
INSERT INTO chat_messages (msg_id, conversation_id, user_id, msg_type, content)
SELECT
    MD5(RAND()),
    1,
    1 + FLOOR(RAND() * 10),
    1,
    CONCAT('test-message-', n)
FROM (
    SELECT (a.n + b.n * 10 + c.n * 100 + d.n * 1000) AS n
    FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
          SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
         (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
          SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
         (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
          SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c,
         (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
          SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) d
) nums
WHERE nums.n < 50000;

-- ------------------------------------------------------------
-- B. 反面教材：深偏移分页
--    要第 40000 条之后的一页 —— 引擎必须扫过并丢弃前 40000 行
--    观察：type、rows
-- ------------------------------------------------------------
EXPLAIN
SELECT id, user_id, content, create_time
FROM chat_messages
WHERE user_id = 1
ORDER BY create_time DESC, id DESC
LIMIT 40000, 20;

-- ------------------------------------------------------------
-- C. 正确姿势：游标分页（拿上一页最后一行的 create_time + id 做锚点）
--    从锚点继续向前取 20 行，扫描量 ~ 20 行级别，与页深度无关
--    观察：type（应为 range 或可用 idx_user_time 且 rows 个位/十位级别）
-- ------------------------------------------------------------
EXPLAIN
SELECT id, user_id, content, create_time
FROM chat_messages
WHERE user_id = 1
  AND (create_time, id) < ('2026-01-01 00:00:00', 9000000)
ORDER BY create_time DESC, id DESC
LIMIT 20;

-- ------------------------------------------------------------
-- D. 思考题（写完再做，别抄答案）：
-- 1) 为什么游标里除了 create_time 还要带上 id？
--    提示：同一条 create_time 下可能挤着多条消息，(create_time,id) 元组才是全序。
-- 2) ORDER BY 里的 , id DESC 去掉会怎样？分页会出什么故事？（重复/漏数据）
-- 3) 为什么是复合排序 (create_time DESC, id DESC)？它和二元素游标怎么对得上？
--    提示：读 B+ 树时 DESC 对等值段的 range 走相反方向（backward scan），
--    若索引方向不够，可考虑 8.0 DESCENDING 索引或对 create_time 做等值重映射。
--    你的 task：分别在表上 EXPLAIN 出两种方向的计划，并讲清 rows 差别。
-- ------------------------------------------------------------

-- ============================================================
-- 游标分页 vs 偏移分页 —— EXPLAIN / EXPLAIN ANALYZE 对比实验
-- 简历 bullet：LIMIT offset 深分页 → 基于上一页末条记录 id 的游标分页，
--              通过 EXPLAIN 验证查询由全表扫描（type=ALL）变为索引范围扫描（type=range）
--
-- 运行方法（任选其一）：
--   docker exec -i easychat-mysql mysql -uroot -pdev123 easychat < sql/02_explain_offset_vs_cursor.sql
--   ./sqltool load /path/to/sql/02_explain_offset_vs_cursor.sql
-- ============================================================

USE easychat;

-- 可重复执行：先清空，避免重复灌数撞 uk_msg_id。
-- 注意：会删掉本表已有数据，只在本地实验库执行。
TRUNCATE TABLE chat_messages;

-- ------------------------------------------------------------
-- A. 造数：5 万条消息，平均分给 10 个用户（每人 5000 条）
--
--    两个坑（我踩过，写在这里省得你再踩）：
--    1) 不能写 MD5(RAND())：MySQL 在 INSERT ... SELECT 里会把 RAND() 当常量，
--       5 万行算出同一个 msg_id，直接撞 uk_msg_id 唯一键。
--    2) 派生表要凑够 5 个数字表（a~e）才是 0..99999；
--       只用 4 个最多 9999 行，WHERE n < 50000 会静静地只插 1 万条。
--
--    create_time 用 n 递增（每行差 1 秒），保证 (create_time, id) 是全序，
--    这样游标分页才有真实的二分定位效果。
-- ------------------------------------------------------------
INSERT INTO chat_messages (msg_id, conversation_id, user_id, msg_type, content, create_time)
SELECT
    MD5(CONCAT('seed-', n)),
    1 + (n % 20),
    1 + (n % 10),
    1,
    CONCAT('test-message-', n),
    '2026-01-01 00:00:00' + INTERVAL n SECOND
FROM (
    SELECT (a.n + b.n * 10 + c.n * 100 + d.n * 1000 + e.n * 10000) AS n
    FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
          SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
         (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
          SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
         (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
          SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c,
         (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
          SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) d,
         (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
          SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) e
) nums
WHERE nums.n < 50000;

-- 更新索引统计信息。不做这一步，优化器可能拿着建表时的空表统计
-- 估出 rows=1，执行计划不可信（这也是"本地 EXPLAIN 和线上不一致"的常见原因）。
ANALYZE TABLE chat_messages;
-- 注意：即便 ANALYZE 过，EXPLAIN 的 rows 仍然是估算值（下面 B 段会看到
-- rows=9816，而该用户实际只有 5000 条）。这不是 bug，是优化器用索引
-- 采样估算的结果 —— 所以证明"深分页扫得多"必须看 EXPLAIN ANALYZE 的
-- actual rows，不能只看 EXPLAIN。

-- ------------------------------------------------------------
-- 数据自检：应为 50000 / 5000
-- ------------------------------------------------------------
SELECT COUNT(*) AS total_rows FROM chat_messages;
SELECT COUNT(*) AS rows_of_user_1 FROM chat_messages WHERE user_id = 1;

-- ------------------------------------------------------------
-- B. 反面教材：深偏移分页
--    取 user_id=1 的第 4000 页之后 20 条（该用户共 5000 条）。
--
--    B+ 树能定位到 user_id=1 的区间（ref），但 LIMIT 40000,20 的语义是
--    "先按顺序产出 40020 行、丢掉前 40000 行"，所以引擎必须真的扫过 4 万条。
--    EXPLAIN 的 rows 只反映"等值前缀能匹配多少行"，看不出深分页代价 ——
--    必须用 EXPLAIN ANALYZE 看 actual rows。
-- ------------------------------------------------------------
EXPLAIN
SELECT id, user_id, content, create_time
FROM chat_messages
WHERE user_id = 1
ORDER BY create_time DESC, id DESC
LIMIT 4000, 20;

EXPLAIN ANALYZE
SELECT id, user_id, content, create_time
FROM chat_messages
WHERE user_id = 1
ORDER BY create_time DESC, id DESC
LIMIT 4000, 20;

-- ------------------------------------------------------------
-- C. 正确姿势：游标分页
--    客户端带上上一页最后一行的 (create_time, id) 作为锚点，服务端查：
--        WHERE user_id = ? AND create_time < ? ORDER BY create_time DESC LIMIT 20
--
--    等值前缀 user_id + 范围前缀 create_time 正好命中 idx_user_time，
--    B+ 树直接跳到锚点位置向前读 20 行 —— 实际扫描量与页深度无关。
--
--    锚点取 B 段"上一页最后一行"的时间：user_id=1 的第 4000 条是 n=10000，
--    create_time = 2026-01-01 00:00:00 + 10000 秒 = 02:46:40。
--    真实业务里这个值由上一页响应的 next_cursor 传回来，客户端不解析。
-- ------------------------------------------------------------
EXPLAIN
SELECT id, user_id, content, create_time
FROM chat_messages
WHERE user_id = 1
  AND create_time < '2026-01-01 02:46:40'
ORDER BY create_time DESC
LIMIT 20;

EXPLAIN ANALYZE
SELECT id, user_id, content, create_time
FROM chat_messages
WHERE user_id = 1
  AND create_time < '2026-01-01 02:46:40'
ORDER BY create_time DESC
LIMIT 20;

-- ------------------------------------------------------------
-- D. 对照实验：把索引摘掉再跑一遍
--    这一步是为了拿到简历里"type=ALL → type=range"的原始证据。
--    （生产环境不要随便 DROP INDEX，这里只是本地实验。）
-- ------------------------------------------------------------
DROP INDEX idx_user_time ON chat_messages;

-- D1. 无索引 + 深偏移：type=ALL + Using filesort，扫描全表
EXPLAIN
SELECT id, user_id, content, create_time
FROM chat_messages
WHERE user_id = 1
ORDER BY create_time DESC, id DESC
LIMIT 4000, 20;

-- D2. 无索引 + 游标：同样是 type=ALL（没有索引可用，游标一点忙都帮不上）
EXPLAIN
SELECT id, user_id, content, create_time
FROM chat_messages
WHERE user_id = 1
  AND create_time < '2026-01-01 02:46:40'
ORDER BY create_time DESC
LIMIT 20;

-- D3. 把组合索引加回来
CREATE INDEX idx_user_time ON chat_messages (user_id, create_time);
ANALYZE TABLE chat_messages;

-- D4. 建索引后 + 游标：type=range，只扫锚点附近 20 行
EXPLAIN
SELECT id, user_id, content, create_time
FROM chat_messages
WHERE user_id = 1
  AND create_time < '2026-01-01 02:46:40'
ORDER BY create_time DESC
LIMIT 20;

-- ------------------------------------------------------------
-- E. 思考题（写完再对答案，别直接抄）：
--
-- 1) EXPLAIN 里 B 段 rows 和 C 段 rows 差多少？EXPLAIN ANALYZE 的
--    actual rows 又差多少？为什么 EXPLAIN 的 rows 不能单独用来证明
--    "深分页更慢"？
--
-- 2) 游标里为什么除了 create_time 还要带上 id？
--    提示：同一秒内可能挤着多条消息，(create_time, id) 才是全序。
--    只带 create_time 会漏掉/重复同一时刻的消息。
--
-- 3) ORDER BY 后面的 id DESC 去掉会怎样？
--
-- 4) D2 段（无索引 + 游标）还是 type=ALL，说明什么？
--    提示：游标分页解决的是"扫描放大"，不是"没有索引"。
--    索引决定能不能快速定位，游标决定定位之后要不要扫过前面所有行。
--
-- 5) 如果业务上一定要看"某人发的所有消息"（user_id 维度）和
--    "某会话的所有消息"（conversation_id 维度），需要几个索引？
--    现有 idx_conv_time 和 idx_user_time 分别服务哪种查询？
-- ------------------------------------------------------------

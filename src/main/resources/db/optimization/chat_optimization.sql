-- 聊天记录表优化脚本
-- 目标：百万级数据查询响应时间从200ms缩短至50ms

-- 1. 创建核心索引（用户ID+时间组合索引）
CREATE INDEX IF NOT EXISTS idx_chat_histories_sender_time 
ON chat_histories(sender_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_chat_histories_receiver_time 
ON chat_histories(receiver_id, create_time DESC);

-- 2. 会话相关索引优化
CREATE INDEX IF NOT EXISTS idx_chat_histories_session_time 
ON chat_histories(session_id, create_time DESC);

-- 3. 未读消息查询优化索引
CREATE INDEX IF NOT EXISTS idx_chat_histories_receiver_read 
ON chat_histories(receiver_id, has_read, create_time DESC);

-- 4. 复合索引：会话ID + 消息类型 + 时间（用于多媒体消息查询）
CREATE INDEX IF NOT EXISTS idx_chat_histories_session_type_time 
ON chat_histories(session_id, type, create_time DESC);

-- 5. 分页查询优化索引
CREATE INDEX IF NOT EXISTS idx_chat_histories_session_id_time 
ON chat_histories(session_id, id, create_time DESC);

-- 6. 聊天会话表索引优化
CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_update_time 
ON chat_sessions(user_id, update_time DESC);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_friend_update_time 
ON chat_sessions(friend_user_id, update_time DESC);

-- 7. 用户表索引优化
CREATE INDEX IF NOT EXISTS idx_users_status_active_time 
ON users(status, last_active_time DESC);

-- 8. 分析表统计信息
ANALYZE TABLE chat_histories;
ANALYZE TABLE chat_sessions;
ANALYZE TABLE users;

-- 9. 查询优化建议

-- 原始慢查询示例（可能的问题）：
-- SELECT * FROM chat_histories 
-- WHERE session_id = ? 
-- ORDER BY create_time DESC 
-- LIMIT 20 OFFSET 1000;

-- 优化后的查询（使用游标分页）：
-- SELECT * FROM chat_histories 
-- WHERE session_id = ? AND id < ? 
-- ORDER BY id DESC 
-- LIMIT 20;

-- 10. 分区建议（适用于大数据量场景）
-- 按月分区聊天记录表
/*
ALTER TABLE chat_histories 
PARTITION BY RANGE (TO_DAYS(create_time)) (
    PARTITION p202401 VALUES LESS THAN (TO_DAYS('2024-02-01')),
    PARTITION p202402 VALUES LESS THAN (TO_DAYS('2024-03-01')),
    PARTITION p202403 VALUES LESS THAN (TO_DAYS('2024-04-01')),
    PARTITION p202404 VALUES LESS THAN (TO_DAYS('2024-05-01')),
    PARTITION p202405 VALUES LESS THAN (TO_DAYS('2024-06-01')),
    PARTITION p202406 VALUES LESS THAN (TO_DAYS('2024-07-01')),
    PARTITION p202407 VALUES LESS THAN (TO_DAYS('2024-08-01')),
    PARTITION p202408 VALUES LESS THAN (TO_DAYS('2024-09-01')),
    PARTITION p202409 VALUES LESS THAN (TO_DAYS('2024-10-01')),
    PARTITION p202410 VALUES LESS THAN (TO_DAYS('2024-11-01')),
    PARTITION p202411 VALUES LESS THAN (TO_DAYS('2024-12-01')),
    PARTITION p202412 VALUES LESS THAN (TO_DAYS('2025-01-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
*/

-- 11. 慢查询监控设置
-- SET GLOBAL slow_query_log = 'ON';
-- SET GLOBAL long_query_time = 0.05; -- 记录执行时间超过50ms的查询

-- 12. 表维护建议
-- 定期清理过期数据（保留最近6个月的聊天记录）
-- DELETE FROM chat_histories WHERE create_time < DATE_SUB(NOW(), INTERVAL 6 MONTH);

-- 13. 索引使用情况监控
-- SELECT 
--     TABLE_NAME,
--     INDEX_NAME,
--     CARDINALITY,
--     SUB_PART,
--     PACKED,
--     NULLABLE,
--     INDEX_TYPE
-- FROM information_schema.STATISTICS 
-- WHERE TABLE_SCHEMA = 'easychat_db' 
-- AND TABLE_NAME IN ('chat_histories', 'chat_sessions', 'users')
-- ORDER BY TABLE_NAME, SEQ_IN_INDEX;
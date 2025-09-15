-- 特别关心功能数据库索引优化脚本
-- 用于提升查询性能和优化数据库操作

-- 1. 特别关心表索引优化
-- 主要查询场景：根据用户ID查询特别关心列表、检查特别关心关系是否存在

-- 复合索引：用户ID + 目标用户ID（用于快速检查关系是否存在）
CREATE INDEX IF NOT EXISTS idx_special_attentions_user_target 
ON special_attentions(user_id, target_user_id);

-- 复合索引：用户ID + 创建时间（用于按时间排序的列表查询）
CREATE INDEX IF NOT EXISTS idx_special_attentions_user_create_time 
ON special_attentions(user_id, create_time DESC);

-- 复合索引：目标用户ID + 在线通知设置（用于查找需要通知的用户）
CREATE INDEX IF NOT EXISTS idx_special_attentions_target_online_notify 
ON special_attentions(target_user_id, online_notification) 
WHERE online_notification = 1;

-- 复合索引：目标用户ID + 消息通知设置（用于消息通知查询）
CREATE INDEX IF NOT EXISTS idx_special_attentions_target_message_notify 
ON special_attentions(target_user_id, message_notification) 
WHERE message_notification = 1;

-- 2. 用户状态表索引优化
-- 主要查询场景：根据用户ID查询状态、根据状态查询用户列表、根据活跃时间清理数据

-- 复合索引：状态 + 最后活跃时间（用于按状态和时间查询）
CREATE INDEX IF NOT EXISTS idx_user_status_status_active_time 
ON user_status(status, last_active_time);

-- 索引：最后活跃时间（用于清理长时间未活跃的用户）
CREATE INDEX IF NOT EXISTS idx_user_status_last_active_time 
ON user_status(last_active_time);

-- 3. 通知记录表索引优化
-- 主要查询场景：根据用户ID查询通知、根据创建时间清理数据

-- 复合索引：用户ID + 是否已读 + 创建时间（用于查询未读通知）
CREATE INDEX IF NOT EXISTS idx_attention_notifications_user_read_time 
ON attention_notifications(user_id, is_read, create_time DESC);

-- 复合索引：目标用户ID + 通知类型（用于统计分析）
CREATE INDEX IF NOT EXISTS idx_attention_notifications_target_type 
ON attention_notifications(target_user_id, notification_type);

-- 索引：创建时间（用于清理过期通知）
CREATE INDEX IF NOT EXISTS idx_attention_notifications_create_time 
ON attention_notifications(create_time);

-- 4. 查询优化建议

-- 4.1 特别关心列表查询优化
-- 原查询可能的问题：JOIN操作可能导致性能问题
-- 建议：使用分步查询，先查询特别关心关系，再批量查询用户信息

-- 4.2 批量操作优化
-- 建议：使用批量插入/更新语句，减少数据库交互次数
-- 示例：INSERT INTO special_attentions (...) VALUES (...), (...), (...)

-- 4.3 分页查询优化
-- 建议：使用LIMIT OFFSET进行分页，但要注意深度分页的性能问题
-- 对于深度分页，可以使用基于ID的分页方式

-- 5. 表分析和统计信息更新
-- 定期更新表的统计信息，帮助查询优化器选择更好的执行计划

-- 分析特别关心表
ANALYZE TABLE special_attentions;

-- 分析用户状态表
ANALYZE TABLE user_status;

-- 分析通知记录表
ANALYZE TABLE attention_notifications;

-- 6. 查询性能监控
-- 建议开启慢查询日志，监控执行时间超过阈值的查询
-- SET GLOBAL slow_query_log = 'ON';
-- SET GLOBAL long_query_time = 0.5; -- 记录执行时间超过0.5秒的查询

-- 7. 表维护建议
-- 定期清理过期数据，保持表的性能
-- 示例：删除30天前的通知记录
-- DELETE FROM attention_notifications WHERE create_time < DATE_SUB(NOW(), INTERVAL 30 DAY);

-- 8. 分区建议（适用于大数据量场景）
-- 如果通知记录表数据量很大，可以考虑按时间分区
-- ALTER TABLE attention_notifications 
-- PARTITION BY RANGE (TO_DAYS(create_time)) (
--     PARTITION p202401 VALUES LESS THAN (TO_DAYS('2024-02-01')),
--     PARTITION p202402 VALUES LESS THAN (TO_DAYS('2024-03-01')),
--     ...
-- );
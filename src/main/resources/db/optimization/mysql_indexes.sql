-- MySQL 索引优化脚本（请在库 easychat_db 执行）
-- 目标：提升聊天历史游标分页、最新消息查询、未读统计与离线拉取、最近聊天列表排序性能

USE easychat_db;

-- chat_histories：游标分页与最新消息
ALTER TABLE chat_histories ADD INDEX idx_ch_session_id_id (session_id, id);
-- 如更适合基于时间查询，可改为：
-- ALTER TABLE chat_histories ADD INDEX idx_ch_session_id_ct (session_id, create_time);

-- chat_histories：未读统计与离线拉取
ALTER TABLE chat_histories ADD INDEX idx_ch_receiver_hasread_ct (receiver_id, has_read, create_time);

-- chat_sessions：最近聊天列表（按更新时间排序）
ALTER TABLE chat_sessions ADD INDEX idx_cs_user_update (user_id, update_time);

-- 注意：重复执行会报索引已存在错误，请先检查 INFORMATION_SCHEMA 或在工具中判断后执行。

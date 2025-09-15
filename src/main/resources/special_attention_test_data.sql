-- 特别关心测试数据

-- 为wangchuang用户添加特别关心数据
INSERT IGNORE INTO special_attention (user_id, target_user_id, online_notification, offline_notification, message_notification, status_change_notification, create_time, update_time) VALUES
('7756640763001649775', '7756640763001649776', 1, 1, 1, 1, NOW(), NOW()),
('7756640763001649775', '7756640763001649777', 1, 0, 1, 1, NOW(), NOW());

-- 为Alice用户添加特别关心数据
INSERT IGNORE INTO special_attention (user_id, target_user_id, online_notification, offline_notification, message_notification, status_change_notification, create_time, update_time) VALUES
('7756640763001649776', '7756640763001649775', 1, 1, 1, 0, NOW(), NOW()),
('7756640763001649776', '7756640763001649777', 1, 1, 0, 1, NOW(), NOW());

-- 添加用户状态数据
INSERT IGNORE INTO user_status (user_id, status, last_active_time, create_time, update_time) VALUES
('7756640763001649775', 'online', NOW(), NOW(), NOW()),
('7756640763001649776', 'online', DATE_SUB(NOW(), INTERVAL 5 MINUTE), NOW(), NOW()),
('7756640763001649777', 'offline', DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW(), NOW()),
('7756640763001649778', 'away', DATE_SUB(NOW(), INTERVAL 30 MINUTE), NOW(), NOW()),
('7756640763001649779', 'busy', DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW(), NOW()),
('7756640763001649780', 'offline', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW());
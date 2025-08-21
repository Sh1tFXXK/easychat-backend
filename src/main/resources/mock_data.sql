-- 清空现有数据（可选）
-- SET FOREIGN_KEY_CHECKS = 0;
-- TRUNCATE TABLE users;
-- TRUNCATE TABLE user_friends;
-- TRUNCATE TABLE chat_histories;
-- TRUNCATE TABLE friend_verifies;
-- TRUNCATE TABLE chat_sessions;
-- TRUNCATE TABLE user_tags;
-- TRUNCATE TABLE verify_codes;
-- SET FOREIGN_KEY_CHECKS = 1;

-- 插入用户数据（避免主键冲突）
INSERT IGNORE INTO users (id, username, nickname, password, email, phone, avatar, gender, birthday, region, introduction, status, create_time, update_time) VALUES
('7756640763001649776', 'alice', 'Alice', '$2a$10$wVzOjlP1hNG.B824n.6yUuM3/M8C2DOjBWh.LOjQwDvOIx5eD1vqG', 'alice@example.com', '13800000001', 'https://example.com/avatar1.png', 1, '1992-05-15', '上海', '热爱编程的工程师', 1, NOW(), NOW()),
('7756640763001649777', 'bob', 'Bob', '$2a$10$vLw2udnV/GD/GFaY.X5b5eQqYyPb0ZhjJUcI6l/59CPeB4HJjUyUu', 'bob@example.com', '13800000002', 'https://example.com/avatar2.png', 1, '1990-11-23', '北京', '产品经理', 1, NOW(), NOW()),
('7756640763001649778', 'cathy', 'Cathy', '$2a$10$HnWcEJ4iX0B5ZQ5r1DzGMOzP1Q7.1Yx8Qx5N7x9K1D3Y9x6S1N7x.', 'cathy@example.com', '13800000003', 'https://example.com/avatar3.png', 0, '1995-03-08', '广州', 'UI设计师', 1, NOW(), NOW()),
('7756640763001649779', 'david', 'David', '$2a$10$NwA1K1zB5nB1xN8W1iA1yOyS1W1S1N1K1zB5nB1xN8W1iA1yOyS1W', 'david@example.com', '13800000004', 'https://example.com/avatar4.png', 1, '1988-07-30', '深圳', '全栈开发者', 0, NOW(), NOW()),
('7756640763001649780', 'eva', 'Eva', '$2a$10$MxZ1K1zB5nB1xN8W1iA1yOyS1W1S1N1K1zB5nB1xN8W1iA1yOyS1W', 'eva@example.com', '13800000005', 'https://example.com/avatar5.png', 0, '1993-12-12', '杭州', '测试工程师', 1, NOW(), NOW());

-- 插入用户标签数据
INSERT IGNORE INTO user_tags (user_id, tag, create_time) VALUES
('7756640763001649776', '程序员', NOW()),
('7756640763001649776', 'Java', NOW()),
('7756640763001649777', '产品', NOW()),
('7756640763001649777', '项目经理', NOW()),
('7756640763001649778', '设计师', NOW()),
('7756640763001649778', 'UI', NOW()),
('7756640763001649779', '全栈', NOW()),
('7756640763001649779', '运维', NOW()),
('7756640763001649780', '测试', NOW()),
('7756640763001649780', '自动化', NOW());

-- 插入好友关系数据
INSERT IGNORE INTO user_friends (user_id, friend_user_id, friend_remark, session_id, session_time, create_time) VALUES
('7756640763001649776', '7756640763001649777', '产品经理Bob', '7756640763001649800', NOW(), NOW()),
('7756640763001649777', '7756640763001649776', '程序员Alice', '7756640763001649800', NOW(), NOW()),
('7756640763001649776', '7756640763001649778', '设计师Cathy', '7756640763001649801', NOW(), NOW()),
('7756640763001649778', '7756640763001649776', '程序员Alice', '7756640763001649801', NOW(), NOW()),
('7756640763001649777', '7756640763001649779', '开发者David', '7756640763001649802', NOW(), NOW()),
('7756640763001649779', '7756640763001649777', '产品经理Bob', '7756640763001649802', NOW(), NOW());

-- 插入会话数据 (修复主键冲突问题，每个用户对应一个会话记录)
INSERT IGNORE INTO chat_sessions (session_id, user_id, friend_user_id, create_time, update_time) VALUES
('7756640763001649800', '7756640763001649776', '7756640763001649777', NOW(), NOW()),
('7756640763001649801', '7756640763001649776', '7756640763001649778', NOW(), NOW()),
('7756640763001649802', '7756640763001649777', '7756640763001649779', NOW(), NOW());

-- 插入聊天记录数据
INSERT IGNORE INTO chat_histories (id, sender_id, receiver_id, session_id, type, content, has_read, show_time, create_time) VALUES
('7756640763001649900', '7756640763001649776', '7756640763001649777', '7756640763001649800', 1, '你好，这个需求文档我看过了，有几个问题需要确认一下。', 1, 1, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
('7756640763001649901', '7756640763001649777', '7756640763001649776', '7756640763001649800', 1, '好的，你说吧，我在会议室等你。', 1, 1, DATE_SUB(NOW(), INTERVAL 55 MINUTE)),
('7756640763001649902', '7756640763001649776', '7756640763001649777', '7756640763001649800', 1, '主要是关于用户权限这部分，我觉得需要再细化一下。', 1, 1, DATE_SUB(NOW(), INTERVAL 50 MINUTE)),
('7756640763001649903', '7756640763001649778', '7756640763001649776', '7756640763001649801', 1, 'Alice，我设计的新界面图已经发你邮箱了，帮忙看看。', 1, 1, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
('7756640763001649904', '7756640763001649776', '7756640763001649778', '7756640763001649801', 1, '好的，我稍后看一下，等会儿给你反馈。', 1, 1, DATE_SUB(NOW(), INTERVAL 25 MINUTE));

-- 插入好友验证数据
INSERT IGNORE INTO friend_verifies (sender_id, receiver_id, apply_reason, remark, status, has_read, create_time) VALUES
('7756640763001649779', '7756640763001649776', '我是David，听说你是优秀的程序员，希望能和你交流学习。', 'David', 0, 0, DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
('7756640763001649780', '7756640763001649776', '你好，我是测试工程师Eva，希望可以加个好友。', 'Eva', 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY));
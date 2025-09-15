-- 用户状态表
CREATE TABLE IF NOT EXISTS user_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id VARCHAR(19) NOT NULL COMMENT '用户ID',
    status VARCHAR(20) NOT NULL DEFAULT 'offline' COMMENT '用户状态：online-在线，offline-离线，away-离开，busy-忙碌',
    last_active_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    socket_id VARCHAR(100) NULL COMMENT 'Socket连接ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_user_id (user_id),
    KEY idx_status (status),
    KEY idx_last_active_time (last_active_time),
    KEY idx_socket_id (socket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户状态表';

-- 特别关心表
CREATE TABLE IF NOT EXISTS special_attention (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id VARCHAR(19) NOT NULL COMMENT '用户ID',
    target_user_id VARCHAR(19) NOT NULL COMMENT '被关心的用户ID',
    online_notification TINYINT(1) NOT NULL DEFAULT 1 COMMENT '上线通知开关：1-开启，0-关闭',
    offline_notification TINYINT(1) NOT NULL DEFAULT 1 COMMENT '离线通知开关：1-开启，0-关闭',
    message_notification TINYINT(1) NOT NULL DEFAULT 1 COMMENT '消息通知开关：1-开启，0-关闭',
    status_change_notification TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态变化通知开关：1-开启，0-关闭',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_user_target (user_id, target_user_id),
    KEY idx_user_id (user_id),
    KEY idx_target_user_id (target_user_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='特别关心表';

-- 关心通知表
CREATE TABLE IF NOT EXISTS attention_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id VARCHAR(19) NOT NULL COMMENT '接收通知的用户ID',
    target_user_id VARCHAR(19) NOT NULL COMMENT '触发通知的用户ID',
    notification_type VARCHAR(50) NOT NULL COMMENT '通知类型：online-上线，offline-离线，message-消息，status_change-状态变化',
    content TEXT NOT NULL COMMENT '通知内容',
    is_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读：1-已读，0-未读',
    priority TINYINT(1) NOT NULL DEFAULT 1 COMMENT '优先级：1-普通，2-高，3-紧急',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    read_time DATETIME NULL COMMENT '阅读时间',
    
    KEY idx_user_id (user_id),
    KEY idx_target_user_id (target_user_id),
    KEY idx_type (notification_type),
    KEY idx_is_read (is_read),
    KEY idx_create_time (create_time),
    KEY idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关心通知表';
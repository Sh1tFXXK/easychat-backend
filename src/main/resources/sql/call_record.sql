-- 通话记录表
CREATE TABLE IF NOT EXISTS call_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    call_id VARCHAR(64) NOT NULL COMMENT '通话ID',
    caller_id VARCHAR(32) NOT NULL COMMENT '发起者ID',
    receiver_id VARCHAR(32) NOT NULL COMMENT '接收者ID',
    group_id VARCHAR(32) NULL COMMENT '群组ID（群通话时使用）',
    call_type VARCHAR(16) NOT NULL COMMENT '通话类型：audio/video',
    duration BIGINT DEFAULT 0 COMMENT '通话时长（秒）',
    start_time DATETIME NOT NULL COMMENT '通话开始时间',
    end_time DATETIME NULL COMMENT '通话结束时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_caller_id (caller_id),
    INDEX idx_receiver_id (receiver_id),
    INDEX idx_group_id (group_id),
    INDEX idx_call_id (call_id),
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通话记录表';
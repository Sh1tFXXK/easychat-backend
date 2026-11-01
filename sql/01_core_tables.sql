-- ============================================================
-- easychat Go 重写 · 核心三张表
-- 目标库：MySQL 8.x / utf8mb4
-- 对应简历第一条：基于 goroutine + WebSocket 的 IM 后端
-- 使用方法见 README.md
-- ============================================================

-- ------------------------------------------------------------
-- 1. 用户表
--    登录用，字段从简（重写后台接口时再按需要加）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username      VARCHAR(32)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'bcrypt后密码',
    nickname      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '昵称',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. 会话表 + 成员表
--    conversation 一对多 conversation_members —— 单聊复用同一张表：
--    私聊也是仅2名成员的会话，路由与未读逻辑只走一条通道
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversations (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    type       TINYINT      NOT NULL DEFAULT 0 COMMENT '0私聊 1群聊',
    title      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '群标题（私聊为空）',
    owner_id   BIGINT UNSIGNED NOT NULL COMMENT '创建者',
    last_msg_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '冗余最后消息ID，列表页免扫消息表',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT='会话表';

CREATE TABLE IF NOT EXISTS conversation_members (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '成员用户ID',
    unread_count    INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '未读数（Redis 计数的持久化兜底）',
    joined_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_conv_user (conversation_id, user_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT='会话成员表';

-- ------------------------------------------------------------
-- 3. 聊天记录表（简历 bullet 的正面主角）
--    组合索引 idx_user_time(user_id, create_time)：
--      · user_id 等值 + create_time 范围/排序 —— B+树前缀定位，扫描范围从"全表"变"该用户区间"
--      · 顺序不可颠倒：先等值后范围，前缀失效即退化为 range 以下
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_messages (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID（游标分页的锚点）',
    msg_id          CHAR(32)     NOT NULL COMMENT '业务消息UUID——Kafka幂等落库去重键（阶段4用）',
    conversation_id BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '发送者用户ID（对应简历的 (user_id, create_time) 组合索引）',
    msg_type        TINYINT      NOT NULL DEFAULT 1 COMMENT '1文本 2图片 3语音…',
    content         TEXT         NOT NULL COMMENT '消息体（含JSON图片路径等）',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0已投递 1已撤回',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 会话内游标分页：conversation_id 等值 + create_time 范围/排序
    KEY idx_conv_time (conversation_id, create_time),
    -- 用户维度的历史记录（简历描述：聊天记录表以 (user_id, create_time) 组合索引）
    KEY idx_user_time (user_id, create_time),
    -- 消息幂等唯一键：Kafka重放/客户端重发不会产生双条
    UNIQUE KEY uk_msg_id (msg_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT='聊天消息表';

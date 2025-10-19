-- 收藏功能相关数据表
-- 创建时间: 2024-01-01
-- 描述: EasyChat 收藏功能的数据库表结构

-- 1. 收藏分类表
CREATE TABLE IF NOT EXISTS `favorite_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `icon` VARCHAR(50) DEFAULT 'other' COMMENT '分类图标',
    `color` VARCHAR(20) DEFAULT '#409EFF' COMMENT '分类颜色',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '分类描述',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `item_count` INT DEFAULT 0 COMMENT '收藏项数量',
    `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_category_name` (`user_id`, `name`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏分类表';

-- 2. 收藏标签表
CREATE TABLE IF NOT EXISTS `favorite_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `use_count` INT DEFAULT 0 COMMENT '使用次数',
    `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tag_name` (`user_id`, `name`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_use_count` (`use_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏标签表';

-- 3. 收藏主表
CREATE TABLE IF NOT EXISTS `favorite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `item_type` VARCHAR(20) NOT NULL COMMENT '收藏类型(message/image/file/voice/video/contact)',
    `title` VARCHAR(200) DEFAULT NULL COMMENT '收藏标题',
    `content` LONGTEXT DEFAULT NULL COMMENT '收藏内容',
    `category_id` BIGINT DEFAULT NULL COMMENT '分类ID',
    `tags` JSON DEFAULT NULL COMMENT '标签列表(JSON数组)',
    `note` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件大小(字节)',
    `file_type` VARCHAR(50) DEFAULT NULL COMMENT '文件类型',
    `original_data` LONGTEXT DEFAULT NULL COMMENT '原始数据(JSON格式)',
    `sender_id` BIGINT DEFAULT NULL COMMENT '发送者ID',
    `sender_name` VARCHAR(100) DEFAULT NULL COMMENT '发送者名称',
    `source_chat_id` VARCHAR(100) DEFAULT NULL COMMENT '来源聊天ID',
    `source_chat_name` VARCHAR(100) DEFAULT NULL COMMENT '来源聊天名称',
    `source_chat_type` VARCHAR(20) DEFAULT NULL COMMENT '来源聊天类型(private/group)',
    `is_available` BOOLEAN DEFAULT TRUE COMMENT '是否可用',
    `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_item_type` (`item_type`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_sender_id` (`sender_id`),
    KEY `idx_source_chat` (`source_chat_id`, `source_chat_type`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_is_available` (`is_available`),
    KEY `idx_user_type_available` (`user_id`, `item_type`, `is_available`),
    FULLTEXT KEY `ft_content_search` (`title`, `content`, `note`),
    CONSTRAINT `fk_favorite_category` FOREIGN KEY (`category_id`) REFERENCES `favorite_category` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏主表';

-- 插入默认分类数据（可选）
INSERT IGNORE INTO `favorite_category` (`user_id`, `name`, `icon`, `color`, `sort_order`) VALUES
(0, '工作', 'work', '#409EFF', 1),
(0, '生活', 'life', '#67C23A', 2),
(0, '重要', 'important', '#E6A23C', 3),
(0, '其他', 'other', '#909399', 4);

-- 创建视图：收藏统计视图
CREATE OR REPLACE VIEW `v_favorite_statistics` AS
SELECT 
    f.user_id,
    COUNT(*) as total_count,
    COUNT(CASE WHEN f.item_type = 'message' THEN 1 END) as message_count,
    COUNT(CASE WHEN f.item_type = 'image' THEN 1 END) as image_count,
    COUNT(CASE WHEN f.item_type = 'file' THEN 1 END) as file_count,
    COUNT(CASE WHEN f.item_type = 'voice' THEN 1 END) as voice_count,
    COUNT(CASE WHEN f.item_type = 'video' THEN 1 END) as video_count,
    COUNT(CASE WHEN f.item_type = 'contact' THEN 1 END) as contact_count,
    SUM(CASE WHEN f.file_size IS NOT NULL THEN f.file_size ELSE 0 END) as total_file_size,
    MAX(f.create_time) as last_favorite_time,
    COUNT(CASE WHEN f.create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 END) as recent_week_count,
    COUNT(CASE WHEN f.create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) THEN 1 END) as recent_month_count
FROM favorite f
WHERE f.is_available = 1
GROUP BY f.user_id;

-- 创建存储过程：清理过期数据
DELIMITER //
CREATE PROCEDURE `sp_cleanup_favorite_data`(
    IN p_user_id BIGINT,
    IN p_days_to_keep INT DEFAULT 365
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- 清理过期的收藏记录（软删除）
    UPDATE favorite 
    SET is_available = FALSE, update_time = NOW()
    WHERE user_id = p_user_id 
    AND create_time < DATE_SUB(NOW(), INTERVAL p_days_to_keep DAY)
    AND is_available = TRUE;
    
    -- 更新分类计数
    UPDATE favorite_category fc
    SET item_count = (
        SELECT COUNT(1) 
        FROM favorite f 
        WHERE f.category_id = fc.id 
        AND f.is_available = TRUE
    )
    WHERE fc.user_id = p_user_id;
    
    -- 清理未使用的标签
    DELETE FROM favorite_tag 
    WHERE user_id = p_user_id 
    AND use_count = 0;
    
    COMMIT;
END //
DELIMITER ;

-- 创建存储过程：用户数据初始化
DELIMITER //
CREATE PROCEDURE `sp_init_user_favorite_data`(
    IN p_user_id BIGINT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- 检查是否已经初始化
    IF (SELECT COUNT(1) FROM favorite_category WHERE user_id = p_user_id) = 0 THEN
        -- 创建默认分类
        INSERT INTO favorite_category (user_id, name, icon, color, sort_order) VALUES
        (p_user_id, '工作', 'work', '#409EFF', 1),
        (p_user_id, '生活', 'life', '#67C23A', 2),
        (p_user_id, '重要', 'important', '#E6A23C', 3),
        (p_user_id, '其他', 'other', '#909399', 4);
    END IF;
    
    COMMIT;
END //
DELIMITER ;

-- 创建触发器：自动更新分类计数
DELIMITER //
CREATE TRIGGER `tr_favorite_insert_update_category_count`
AFTER INSERT ON `favorite`
FOR EACH ROW
BEGIN
    IF NEW.category_id IS NOT NULL AND NEW.is_available = TRUE THEN
        UPDATE favorite_category 
        SET item_count = item_count + 1, update_time = NOW()
        WHERE id = NEW.category_id;
    END IF;
END //

CREATE TRIGGER `tr_favorite_update_update_category_count`
AFTER UPDATE ON `favorite`
FOR EACH ROW
BEGIN
    -- 处理分类变更
    IF OLD.category_id != NEW.category_id OR OLD.is_available != NEW.is_available THEN
        -- 减少旧分类计数
        IF OLD.category_id IS NOT NULL AND OLD.is_available = TRUE THEN
            UPDATE favorite_category 
            SET item_count = GREATEST(item_count - 1, 0), update_time = NOW()
            WHERE id = OLD.category_id;
        END IF;
        
        -- 增加新分类计数
        IF NEW.category_id IS NOT NULL AND NEW.is_available = TRUE THEN
            UPDATE favorite_category 
            SET item_count = item_count + 1, update_time = NOW()
            WHERE id = NEW.category_id;
        END IF;
    END IF;
END //

CREATE TRIGGER `tr_favorite_delete_update_category_count`
AFTER DELETE ON `favorite`
FOR EACH ROW
BEGIN
    IF OLD.category_id IS NOT NULL AND OLD.is_available = TRUE THEN
        UPDATE favorite_category 
        SET item_count = GREATEST(item_count - 1, 0), update_time = NOW()
        WHERE id = OLD.category_id;
    END IF;
END //
DELIMITER ;

-- 创建索引优化查询性能
CREATE INDEX `idx_favorite_user_category_time` ON `favorite` (`user_id`, `category_id`, `create_time` DESC);
CREATE INDEX `idx_favorite_user_type_time` ON `favorite` (`user_id`, `item_type`, `create_time` DESC);
CREATE INDEX `idx_favorite_sender_time` ON `favorite` (`sender_id`, `create_time` DESC);
CREATE INDEX `idx_favorite_source_chat_time` ON `favorite` (`source_chat_id`, `source_chat_type`, `create_time` DESC);

-- 创建复合索引用于统计查询
CREATE INDEX `idx_favorite_stats` ON `favorite` (`user_id`, `is_available`, `item_type`, `create_time`);
CREATE INDEX `idx_category_user_sort` ON `favorite_category` (`user_id`, `sort_order`, `item_count`);
CREATE INDEX `idx_tag_user_count` ON `favorite_tag` (`user_id`, `use_count` DESC, `update_time` DESC);

-- 添加表注释和字段注释
ALTER TABLE `favorite` COMMENT = '收藏主表 - 存储用户收藏的各种内容';
ALTER TABLE `favorite_category` COMMENT = '收藏分类表 - 用户自定义的收藏分类';
ALTER TABLE `favorite_tag` COMMENT = '收藏标签表 - 用户创建的标签及使用统计';

-- 示例数据（测试用，生产环境请删除）
-- INSERT INTO favorite_category (user_id, name, icon, color, sort_order) VALUES
-- (1, '工作文档', 'document', '#409EFF', 1),
-- (1, '生活照片', 'image', '#67C23A', 2),
-- (1, '重要消息', 'message', '#E6A23C', 3);

-- INSERT INTO favorite_tag (user_id, name, use_count) VALUES
-- (1, '重要', 5),
-- (1, '工作', 3),
-- (1, '学习', 2);

-- INSERT INTO favorite (user_id, item_type, title, content, category_id, tags, note, sender_name, source_chat_name, source_chat_type) VALUES
-- (1, 'message', '会议纪要', '今天的会议讨论了项目进度...', 1, '["工作", "重要"]', '需要跟进', '张三', '项目组群', 'group'),
-- (1, 'image', '产品截图', '/uploads/images/product_screenshot.png', 2, '["工作", "产品"]', '产品功能展示', '李四', '产品讨论群', 'group');

-- 创建定时任务（需要开启事件调度器）
-- SET GLOBAL event_scheduler = ON;

-- 每天凌晨2点清理过期数据
-- CREATE EVENT IF NOT EXISTS `ev_cleanup_favorite_data`
-- ON SCHEDULE EVERY 1 DAY
-- STARTS '2024-01-01 02:00:00'
-- DO
-- BEGIN
--     -- 清理超过2年的软删除记录
--     DELETE FROM favorite 
--     WHERE is_available = FALSE 
--     AND update_time < DATE_SUB(NOW(), INTERVAL 2 YEAR);
--     
--     -- 清理未使用的标签（所有用户）
--     DELETE FROM favorite_tag 
--     WHERE use_count = 0 
--     AND create_time < DATE_SUB(NOW(), INTERVAL 30 DAY);
-- END;
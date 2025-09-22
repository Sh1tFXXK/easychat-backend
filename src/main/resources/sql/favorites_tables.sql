-- 收藏功能相关数据库表创建脚本

-- 1. 收藏表 (favorites)
CREATE TABLE IF NOT EXISTS favorites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收藏ID',
    user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
    item_type ENUM('MESSAGE', 'USER', 'GROUP', 'FILE') NOT NULL COMMENT '收藏类型',
    item_id VARCHAR(100) NOT NULL COMMENT '收藏项目ID',
    title VARCHAR(500) NOT NULL COMMENT '收藏标题',
    content TEXT COMMENT '收藏内容',
    category_id BIGINT COMMENT '分类ID',
    tags JSON COMMENT '标签数组',
    notes TEXT COMMENT '用户备注',
    metadata JSON COMMENT '元数据',
    special_care BOOLEAN DEFAULT FALSE COMMENT '是否特别关心',
    special_care_priority ENUM('high', 'medium', 'low') DEFAULT 'medium' COMMENT '特别关心优先级',
    special_care_time DATETIME COMMENT '设置特别关心时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 索引
    INDEX idx_user_id (user_id),
    INDEX idx_item_type (item_type),
    INDEX idx_category_id (category_id),
    INDEX idx_special_care (special_care),
    INDEX idx_created_at (created_at),
    INDEX idx_title (title(100)),
    
    -- 唯一约束：同一用户不能重复收藏同一项目
    UNIQUE KEY uk_user_item (user_id, item_type, item_id),
    
    -- 外键约束
    FOREIGN KEY (category_id) REFERENCES favorite_categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏表';

-- 2. 收藏分类表 (favorite_categories)
CREATE TABLE IF NOT EXISTS favorite_categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    color VARCHAR(7) DEFAULT '#409EFF' COMMENT '分类颜色',
    icon VARCHAR(50) COMMENT '分类图标',
    description TEXT COMMENT '分类描述',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 索引
    INDEX idx_user_id (user_id),
    INDEX idx_sort_order (sort_order),
    
    -- 唯一约束：同一用户的分类名称不能重复
    UNIQUE KEY uk_user_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏分类表';

-- 3. 收藏统计表 (favorite_stats)
CREATE TABLE IF NOT EXISTS favorite_stats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '统计ID',
    user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
    total_count INT DEFAULT 0 COMMENT '总收藏数',
    message_count INT DEFAULT 0 COMMENT '消息收藏数',
    user_count INT DEFAULT 0 COMMENT '用户收藏数',
    group_count INT DEFAULT 0 COMMENT '群组收藏数',
    file_count INT DEFAULT 0 COMMENT '文件收藏数',
    special_care_count INT DEFAULT 0 COMMENT '特别关心数',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 唯一约束：每个用户只有一条统计记录
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏统计表';

-- 4. 创建默认分类的存储过程
DELIMITER //
CREATE PROCEDURE CreateDefaultCategories(IN p_user_id VARCHAR(50))
BEGIN
    -- 为新用户创建默认分类
    INSERT IGNORE INTO favorite_categories (user_id, name, color, icon, description, sort_order) VALUES
    (p_user_id, '默认', '#409EFF', 'folder', '默认分类', 1),
    (p_user_id, '工作', '#67C23A', 'briefcase', '工作相关收藏', 2),
    (p_user_id, '学习', '#E6A23C', 'book', '学习资料收藏', 3),
    (p_user_id, '生活', '#F56C6C', 'home', '生活相关收藏', 4);
END //
DELIMITER ;

-- 5. 创建触发器：自动更新统计数据
DELIMITER //

-- 插入收藏时更新统计
CREATE TRIGGER tr_favorites_insert_stats
AFTER INSERT ON favorites
FOR EACH ROW
BEGIN
    INSERT INTO favorite_stats (user_id, total_count, message_count, user_count, group_count, file_count, special_care_count)
    VALUES (NEW.user_id, 1, 
            CASE WHEN NEW.item_type = 'MESSAGE' THEN 1 ELSE 0 END,
            CASE WHEN NEW.item_type = 'USER' THEN 1 ELSE 0 END,
            CASE WHEN NEW.item_type = 'GROUP' THEN 1 ELSE 0 END,
            CASE WHEN NEW.item_type = 'FILE' THEN 1 ELSE 0 END,
            CASE WHEN NEW.special_care = TRUE THEN 1 ELSE 0 END)
    ON DUPLICATE KEY UPDATE
        total_count = total_count + 1,
        message_count = message_count + CASE WHEN NEW.item_type = 'MESSAGE' THEN 1 ELSE 0 END,
        user_count = user_count + CASE WHEN NEW.item_type = 'USER' THEN 1 ELSE 0 END,
        group_count = group_count + CASE WHEN NEW.item_type = 'GROUP' THEN 1 ELSE 0 END,
        file_count = file_count + CASE WHEN NEW.item_type = 'FILE' THEN 1 ELSE 0 END,
        special_care_count = special_care_count + CASE WHEN NEW.special_care = TRUE THEN 1 ELSE 0 END;
END //

-- 删除收藏时更新统计
CREATE TRIGGER tr_favorites_delete_stats
AFTER DELETE ON favorites
FOR EACH ROW
BEGIN
    UPDATE favorite_stats SET
        total_count = GREATEST(0, total_count - 1),
        message_count = GREATEST(0, message_count - CASE WHEN OLD.item_type = 'MESSAGE' THEN 1 ELSE 0 END),
        user_count = GREATEST(0, user_count - CASE WHEN OLD.item_type = 'USER' THEN 1 ELSE 0 END),
        group_count = GREATEST(0, group_count - CASE WHEN OLD.item_type = 'GROUP' THEN 1 ELSE 0 END),
        file_count = GREATEST(0, file_count - CASE WHEN OLD.item_type = 'FILE' THEN 1 ELSE 0 END),
        special_care_count = GREATEST(0, special_care_count - CASE WHEN OLD.special_care = TRUE THEN 1 ELSE 0 END)
    WHERE user_id = OLD.user_id;
END //

-- 更新收藏时更新统计
CREATE TRIGGER tr_favorites_update_stats
AFTER UPDATE ON favorites
FOR EACH ROW
BEGIN
    IF OLD.special_care != NEW.special_care THEN
        UPDATE favorite_stats SET
            special_care_count = special_care_count + 
                CASE WHEN NEW.special_care = TRUE THEN 1 
                     WHEN OLD.special_care = TRUE THEN -1 
                     ELSE 0 END
        WHERE user_id = NEW.user_id;
    END IF;
END //

DELIMITER ;

-- 6. 创建全文搜索索引（如果需要）
-- ALTER TABLE favorites ADD FULLTEXT(title, content);

-- 7. 插入一些示例数据（可选）
-- INSERT INTO favorite_categories (user_id, name, color, icon, description, sort_order) VALUES
-- ('user123', '重要', '#F56C6C', 'star', '重要内容收藏', 1),
-- ('user123', '待办', '#E6A23C', 'clock', '待办事项收藏', 2);

-- 8. 创建视图：收藏详情视图
CREATE VIEW v_favorite_details AS
SELECT 
    f.id,
    f.user_id,
    f.item_type,
    f.item_id,
    f.title,
    f.content,
    f.category_id,
    f.tags,
    f.notes,
    f.metadata,
    f.special_care,
    f.special_care_priority,
    f.special_care_time,
    f.created_at,
    f.updated_at,
    fc.name as category_name,
    fc.color as category_color,
    fc.icon as category_icon
FROM favorites f
LEFT JOIN favorite_categories fc ON f.category_id = fc.id;

-- 9. 创建索引优化查询性能
CREATE INDEX idx_favorites_user_special ON favorites(user_id, special_care, special_care_priority);
CREATE INDEX idx_favorites_user_category ON favorites(user_id, category_id);
CREATE INDEX idx_favorites_user_type ON favorites(user_id, item_type);
CREATE INDEX idx_favorites_created_desc ON favorites(created_at DESC);

-- 10. 权限设置（根据实际需要调整）
-- GRANT SELECT, INSERT, UPDATE, DELETE ON favorites TO 'easychat_user'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON favorite_categories TO 'easychat_user'@'%';
-- GRANT SELECT, UPDATE ON favorite_stats TO 'easychat_user'@'%';

-- 执行完成提示
SELECT 'Favorites tables created successfully!' as message;
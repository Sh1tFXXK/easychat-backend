# 收藏功能后端API开发文档

## 概述

本文档详细说明了聊天应用收藏功能的后端API设计和实现要求。收藏功能允许用户收藏聊天中的消息、图片、文件、语音等内容，并提供分类管理、标签管理、搜索等功能。

## 数据库设计

### 1. 收藏项表 (favorites)

```sql
CREATE TABLE favorites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    item_type VARCHAR(20) NOT NULL COMMENT '收藏类型: message, image, file, voice, video, contact',
    title VARCHAR(200) NOT NULL COMMENT '收藏标题',
    content TEXT COMMENT '收藏内容',
    original_data JSON COMMENT '原始数据(消息完整信息)',
    category_id BIGINT COMMENT '分类ID',
    tags JSON COMMENT '标签数组',
    note TEXT COMMENT '用户备注',
    source_chat_id VARCHAR(100) COMMENT '来源聊天ID',
    source_chat_name VARCHAR(100) COMMENT '来源聊天名称',
    source_chat_type VARCHAR(20) COMMENT '来源聊天类型: private, group',
    sender_id BIGINT COMMENT '发送者ID',
    sender_name VARCHAR(100) COMMENT '发送者名称',
    file_size BIGINT COMMENT '文件大小(字节)',
    file_type VARCHAR(50) COMMENT '文件类型',
    is_available TINYINT DEFAULT 1 COMMENT '是否可用(文件是否存在)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_category_id (category_id),
    INDEX idx_item_type (item_type),
    INDEX idx_create_time (create_time),
    INDEX idx_source_chat (source_chat_id, source_chat_type)
);
```

### 2. 收藏分类表 (favorite_categories)

```sql
CREATE TABLE favorite_categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    icon VARCHAR(20) DEFAULT 'other' COMMENT '分类图标',
    color VARCHAR(7) DEFAULT '#409EFF' COMMENT '分类颜色',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    item_count INT DEFAULT 0 COMMENT '收藏项数量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_name (user_id, name),
    INDEX idx_user_id (user_id)
);
```

### 3. 收藏标签表 (favorite_tags)

```sql
CREATE TABLE favorite_tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    name VARCHAR(30) NOT NULL COMMENT '标签名称',
    use_count INT DEFAULT 0 COMMENT '使用次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_name (user_id, name),
    INDEX idx_user_id (user_id)
);
```

## API接口设计

### 1. 收藏管理接口

#### 1.1 添加收藏

**接口地址：** `POST /api/favorites`

**请求参数：**
```json
{
    "itemType": "message",
    "title": "这是一条重要消息",
    "content": "消息内容",
    "originalData": {
        "messageId": "123456",
        "senderId": "789",
        "senderName": "张三",
        "createTime": "2024-01-01 12:00:00"
    },
    "categoryId": 1,
    "tags": ["重要", "工作"],
    "note": "这是一个重要的工作消息",
    "sourceChat": {
        "chatId": "chat_123",
        "chatName": "项目讨论组",
        "chatType": "group"
    }
}
```

**响应数据：**
```json
{
    "code": 200,
    "message": "收藏成功",
    "data": {
        "id": 1,
        "itemType": "message",
        "title": "这是一条重要消息",
        "createTime": "2024-01-01 12:00:00"
    }
}
```

#### 1.2 获取收藏列表

**接口地址：** `GET /api/favorites`

**请求参数：**
```
page: 1 (页码)
pageSize: 20 (每页数量)
categoryId: 1 (分类ID，可选)
itemType: message (收藏类型，可选)
keyword: 搜索关键词 (可选)
tags: 标签1,标签2 (标签筛选，可选)
startTime: 2024-01-01 (开始时间，可选)
endTime: 2024-01-31 (结束时间，可选)
sortBy: time (排序字段: time, title, type, category)
sortOrder: desc (排序方向: asc, desc)
```

**响应数据：**
```json
{
    "code": 200,
    "message": "获取成功",
    "data": {
        "list": [
            {
                "id": 1,
                "itemType": "message",
                "title": "这是一条重要消息",
                "content": "消息内容",
                "categoryId": 1,
                "categoryName": "工作",
                "categoryColor": "#409EFF",
                "tags": ["重要", "工作"],
                "note": "这是一个重要的工作消息",
                "sourceChatName": "项目讨论组",
                "senderName": "张三",
                "isAvailable": true,
                "createTime": "2024-01-01 12:00:00"
            }
        ],
        "total": 100,
        "page": 1,
        "pageSize": 20
    }
}
```

#### 1.3 删除收藏

**接口地址：** `DELETE /api/favorites/{id}`

**响应数据：**
```json
{
    "code": 200,
    "message": "删除成功"
}
```

#### 1.4 批量删除收藏

**接口地址：** `DELETE /api/favorites/batch`

**请求参数：**
```json
{
    "ids": [1, 2, 3, 4, 5]
}
```

#### 1.5 更新收藏

**接口地址：** `PUT /api/favorites/{id}`

**请求参数：**
```json
{
    "categoryId": 2,
    "tags": ["重要", "紧急"],
    "note": "更新后的备注"
}
```

#### 1.6 批量更新收藏分类

**接口地址：** `PUT /api/favorites/batch/category`

**请求参数：**
```json
{
    "ids": [1, 2, 3],
    "categoryId": 2
}
```

#### 1.7 批量添加标签

**接口地址：** `PUT /api/favorites/batch/tags/add`

**请求参数：**
```json
{
    "ids": [1, 2, 3],
    "tags": ["新标签1", "新标签2"],
    "operation": "add" // add: 添加标签, replace: 替换标签
}
```

#### 1.8 批量移除标签

**接口地址：** `PUT /api/favorites/batch/tags/remove`

**请求参数：**
```json
{
    "ids": [1, 2, 3],
    "tags": ["要移除的标签1", "要移除的标签2"]
}
```

### 2. 分类管理接口

#### 2.1 获取分类列表

**接口地址：** `GET /api/favorites/categories`

**响应数据：**
```json
{
    "code": 200,
    "message": "获取成功",
    "data": [
        {
            "id": 1,
            "name": "工作",
            "icon": "work",
            "color": "#409EFF",
            "itemCount": 25,
            "createTime": "2024-01-01 12:00:00"
        }
    ]
}
```

#### 2.2 创建分类

**接口地址：** `POST /api/favorites/categories`

**请求参数：**
```json
{
    "name": "学习",
    "icon": "study",
    "color": "#67C23A"
}
```

#### 2.3 更新分类

**接口地址：** `PUT /api/favorites/categories/{id}`

**请求参数：**
```json
{
    "name": "学习资料",
    "icon": "study",
    "color": "#67C23A"
}
```

#### 2.4 删除分类

**接口地址：** `DELETE /api/favorites/categories/{id}`

**请求参数：**
```
moveToCategory: 2 (可选，将该分类下的收藏移动到指定分类)
```

### 3. 标签管理接口

#### 3.1 获取标签列表

**接口地址：** `GET /api/favorites/tags`

**响应数据：**
```json
{
    "code": 200,
    "message": "获取成功",
    "data": [
        {
            "id": 1,
            "name": "重要",
            "useCount": 15,
            "createTime": "2024-01-01 12:00:00"
        }
    ]
}
```

#### 3.2 创建标签

**接口地址：** `POST /api/favorites/tags`

**请求参数：**
```json
{
    "name": "新标签"
}
```

#### 3.3 删除标签

**接口地址：** `DELETE /api/favorites/tags/{id}`

### 4. 搜索接口

#### 4.1 高级搜索

**接口地址：** `POST /api/favorites/search`

**请求参数：**
```json
{
    "keyword": "搜索关键词",
    "itemTypes": ["message", "image", "file"],
    "categoryIds": [1, 2, 3],
    "tags": ["标签1", "标签2"],
    "startTime": "2024-01-01 00:00:00",
    "endTime": "2024-01-31 23:59:59",
    "sourceChatName": "聊天名称",
    "senderName": "发送者名称",
    "minSize": 1024,
    "maxSize": 10485760,
    "availability": "all", // all, available, unavailable
    "sortBy": "time",
    "sortOrder": "desc",
    "page": 1,
    "pageSize": 20
}
```

### 5. 统计接口

#### 5.1 获取收藏统计

**接口地址：** `GET /api/favorites/statistics`

**响应数据：**
```json
{
    "code": 200,
    "message": "获取成功",
    "data": {
        "totalCount": 150,
        "typeStatistics": {
            "message": 80,
            "image": 30,
            "file": 25,
            "voice": 10,
            "video": 5
        },
        "categoryStatistics": [
            {
                "categoryId": 1,
                "categoryName": "工作",
                "count": 60
            }
        ],
        "recentActivity": [
            {
                "date": "2024-01-01",
                "count": 5
            }
        ]
    }
}
```

## 业务逻辑要求

### 1. 收藏添加逻辑

1. **数据验证**：
   - 验证用户权限
   - 验证收藏类型是否支持
   - 验证分类是否属于当前用户
   - 验证标签格式和长度

2. **数据处理**：
   - 自动生成收藏标题（如果未提供）
   - 提取文件信息（大小、类型等）
   - 处理标签（自动创建不存在的标签）
   - 更新分类计数

3. **存储逻辑**：
   - 保存收藏项基本信息
   - 保存原始数据到JSON字段
   - 更新标签使用计数
   - 更新分类项目计数

### 2. 搜索优化

1. **全文搜索**：
   - 支持标题和内容的全文搜索
   - 支持拼音搜索
   - 支持模糊匹配

2. **索引优化**：
   - 为常用查询字段建立索引
   - 考虑使用Elasticsearch提升搜索性能

3. **缓存策略**：
   - 缓存热门搜索结果
   - 缓存用户分类和标签信息

### 3. 文件可用性检查

1. **定期检查**：
   - 定时任务检查文件是否存在
   - 更新文件可用性状态

2. **实时检查**：
   - 用户访问时检查文件状态
   - 提供文件恢复建议

### 4. 数据清理

1. **过期数据清理**：
   - 清理长期未使用的标签
   - 清理空分类

2. **数据一致性**：
   - 定期检查计数字段准确性
   - 修复数据不一致问题

## 性能优化建议

### 1. 数据库优化

1. **索引策略**：
   ```sql
   -- 复合索引
   CREATE INDEX idx_user_category_time ON favorites(user_id, category_id, create_time DESC);
   CREATE INDEX idx_user_type_time ON favorites(user_id, item_type, create_time DESC);
   
   -- 全文索引
   CREATE FULLTEXT INDEX idx_content_search ON favorites(title, content);
   ```

2. **分页优化**：
   - 使用游标分页替代OFFSET
   - 预加载相关数据减少N+1查询

### 2. 缓存策略

1. **Redis缓存**：
   ```
   # 用户分类列表
   user:categories:{userId}
   
   # 用户标签列表
   user:tags:{userId}
   
   # 热门搜索结果
   search:result:{hash}
   ```

2. **缓存更新**：
   - 分类/标签变更时及时更新缓存
   - 设置合理的过期时间

### 3. 接口优化

1. **批量操作**：
   - 支持批量添加、删除、更新
   - 使用事务保证数据一致性

2. **异步处理**：
   - 文件信息提取异步处理
   - 搜索索引异步更新

## 错误处理

### 1. 常见错误码

```json
{
    "FAVORITE_NOT_FOUND": 40001,
    "CATEGORY_NOT_FOUND": 40002,
    "TAG_NOT_FOUND": 40003,
    "DUPLICATE_FAVORITE": 40004,
    "CATEGORY_NAME_EXISTS": 40005,
    "INVALID_FILE_TYPE": 40006,
    "FILE_NOT_AVAILABLE": 40007,
    "PERMISSION_DENIED": 40301,
    "QUOTA_EXCEEDED": 40302
}
```

### 2. 错误响应格式

```json
{
    "code": 40001,
    "message": "收藏项不存在",
    "data": null,
    "timestamp": "2024-01-01T12:00:00Z"
}
```

## 安全考虑

### 1. 权限控制

1. **用户隔离**：
   - 严格验证用户只能操作自己的收藏
   - 防止越权访问其他用户数据

2. **接口限流**：
   - 限制收藏添加频率
   - 防止恶意批量操作

### 2. 数据验证

1. **输入验证**：
   - 验证所有用户输入
   - 防止SQL注入和XSS攻击

2. **文件安全**：
   - 验证文件类型和大小
   - 扫描恶意文件

## 部署建议

### 1. 数据库配置

```sql
-- 建议的MySQL配置
innodb_buffer_pool_size = 1G
innodb_log_file_size = 256M
max_connections = 1000
```

### 2. 监控指标

1. **性能指标**：
   - 接口响应时间
   - 数据库查询性能
   - 缓存命中率

2. **业务指标**：
   - 收藏添加成功率
   - 搜索查询量
   - 用户活跃度

这份文档提供了完整的收藏功能后端开发指南，包括数据库设计、API接口、业务逻辑、性能优化等各个方面。请根据实际需求进行调整和实现。
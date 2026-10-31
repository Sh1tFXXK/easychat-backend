# EasyChat 收藏功能 API 文档

## 概述

EasyChat 收藏功能提供了完整的消息、文件、图片等内容收藏管理能力，支持分类管理、标签系统、高级搜索等功能。

## 基础信息

- **基础URL**: `/api/favorites`
- **认证方式**: JWT Token 或 Session
- **数据格式**: JSON
- **字符编码**: UTF-8

## 通用响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1640995200000
}
```

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 1. 收藏管理

### 1.1 添加收藏

**接口地址**: `POST /api/favorites`

**请求参数**:
```json
{
  "itemType": "message",
  "title": "重要会议纪要",
  "content": "今天的会议讨论了...",
  "categoryId": 1,
  "tags": ["工作", "重要"],
  "note": "需要跟进的事项",
  "fileSize": 1024,
  "fileType": "text",
  "originalData": {
    "messageId": "msg_123",
    "fileName": "document.pdf",
    "duration": 30,
    "senderId": 456,
    "senderName": "张三"
  },
  "sourceChat": {
    "chatId": "chat_789",
    "chatName": "项目组群",
    "chatType": "group"
  }
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "收藏成功",
  "data": {
    "id": 1,
    "itemType": "message",
    "title": "重要会议纪要",
    "content": "今天的会议讨论了...",
    "categoryId": 1,
    "categoryName": "工作",
    "categoryColor": "#409EFF",
    "categoryIcon": "work",
    "tags": ["工作", "重要"],
    "note": "需要跟进的事项",
    "senderName": "张三",
    "sourceChatName": "项目组群",
    "sourceChatType": "group",
    "createTime": "2024-01-01T10:00:00",
    "updateTime": "2024-01-01T10:00:00"
  }
}
```

### 1.2 删除收藏

**接口地址**: `DELETE /api/favorites/{id}`

**路径参数**:
- `id`: 收藏ID

**响应示例**:
```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

### 1.3 批量删除收藏

**接口地址**: `DELETE /api/favorites/batch`

**请求参数**:
```json
[1, 2, 3, 4, 5]
```

### 1.4 更新收藏

**接口地址**: `PUT /api/favorites/{id}`

**请求参数**:
```json
{
  "categoryId": 2,
  "tags": ["工作", "重要", "紧急"],
  "note": "更新后的备注"
}
```

### 1.5 获取收藏详情

**接口地址**: `GET /api/favorites/{id}`

### 1.6 获取收藏列表

**接口地址**: `GET /api/favorites`

**查询参数**:
- `page`: 页码（默认1）
- `pageSize`: 每页大小（默认20）
- `categoryId`: 分类ID
- `itemType`: 内容类型
- `keyword`: 搜索关键词
- `tags`: 标签列表
- `sortBy`: 排序字段（默认createTime）
- `sortOrder`: 排序方向（默认desc）

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "itemType": "message",
        "title": "重要会议纪要",
        "content": "今天的会议讨论了...",
        "categoryName": "工作",
        "tags": ["工作", "重要"],
        "createTime": "2024-01-01T10:00:00"
      }
    ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

### 1.7 高级搜索

**接口地址**: `POST /api/favorites/search`

**请求参数**:
```json
{
  "page": 1,
  "pageSize": 20,
  "keyword": "会议",
  "searchMode": "fuzzy",
  "categoryIds": [1, 2],
  "itemTypes": ["message", "file"],
  "tags": ["工作", "重要"],
  "tagMatchMode": "any",
  "senderName": "张三",
  "sourceChatName": "项目组",
  "sourceChatType": "group",
  "minFileSize": 1024,
  "maxFileSize": 10485760,
  "fileTypes": ["pdf", "doc"],
  "startTime": "2024-01-01T00:00:00",
  "endTime": "2024-01-31T23:59:59",
  "sortBy": "createTime",
  "sortOrder": "desc"
}
```

### 1.8 批量操作

#### 批量更新分类
**接口地址**: `PUT /api/favorites/batch/category`

```json
{
  "ids": [1, 2, 3],
  "categoryId": 2
}
```

#### 批量添加标签
**接口地址**: `PUT /api/favorites/batch/tags/add`

```json
{
  "ids": [1, 2, 3],
  "tags": ["新标签1", "新标签2"],
  "tagOperation": "add"
}
```

#### 批量移除标签
**接口地址**: `PUT /api/favorites/batch/tags/remove`

```json
{
  "ids": [1, 2, 3],
  "tagsToRemove": ["旧标签1", "旧标签2"]
}
```

### 1.9 获取收藏统计

**接口地址**: `GET /api/favorites/statistics`

**响应示例**:
```json
{
  "code": 200,
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
      },
      {
        "categoryId": 2,
        "categoryName": "生活",
        "count": 40
      }
    ],
    "recentActivity": [
      {
        "date": "2024-01-01",
        "count": 5
      },
      {
        "date": "2024-01-02",
        "count": 3
      }
    ]
  }
}
```

### 1.10 检查重复收藏

**接口地址**: `GET /api/favorites/check-duplicate`

**查询参数**:
- `itemType`: 内容类型
- `content`: 内容

**响应示例**:
```json
{
  "code": 200,
  "data": true
}
```

---

## 2. 分类管理

### 2.1 创建分类

**接口地址**: `POST /api/favorites/categories`

**请求参数**:
```json
{
  "name": "学习资料",
  "icon": "book",
  "color": "#67C23A",
  "description": "学习相关的收藏内容",
  "sortOrder": 5
}
```

### 2.2 更新分类

**接口地址**: `PUT /api/favorites/categories/{id}`

### 2.3 删除分类

**接口地址**: `DELETE /api/favorites/categories/{id}`

**查询参数**:
- `moveToCategory`: 可选，将分类下的收藏移动到指定分类

### 2.4 获取分类列表

**接口地址**: `GET /api/favorites/categories`

**响应示例**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "工作",
      "icon": "work",
      "color": "#409EFF",
      "description": "工作相关内容",
      "sortOrder": 1,
      "itemCount": 25,
      "createTime": "2024-01-01T10:00:00"
    }
  ]
}
```

### 2.5 初始化默认分类

**接口地址**: `POST /api/favorites/categories/init`

---

## 3. 标签管理

### 3.1 创建标签

**接口地址**: `POST /api/favorites/tags`

**查询参数**:
- `name`: 标签名称

### 3.2 删除标签

**接口地址**: `DELETE /api/favorites/tags/{id}`

### 3.3 获取标签列表

**接口地址**: `GET /api/favorites/tags`

**响应示例**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "重要",
      "useCount": 15,
      "createTime": "2024-01-01T10:00:00",
      "updateTime": "2024-01-15T14:30:00"
    }
  ]
}
```

### 3.4 获取热门标签

**接口地址**: `GET /api/favorites/tags/popular`

**查询参数**:
- `limit`: 返回数量（默认10）

### 3.5 清理未使用标签

**接口地址**: `DELETE /api/favorites/tags/cleanup`

---

## 4. 数据模型

### 4.1 收藏项 (Favorite)

```typescript
interface Favorite {
  id: number;
  userId: number;
  itemType: 'message' | 'image' | 'file' | 'voice' | 'video' | 'contact';
  title: string;
  content: string;
  categoryId?: number;
  categoryName?: string;
  categoryColor?: string;
  categoryIcon?: string;
  tags: string[];
  note?: string;
  fileSize?: number;
  fileType?: string;
  senderId?: number;
  senderName?: string;
  sourceChatId?: string;
  sourceChatName?: string;
  sourceChatType?: 'private' | 'group';
  isAvailable: boolean;
  createTime: string;
  updateTime: string;
}
```

### 4.2 收藏分类 (FavoriteCategory)

```typescript
interface FavoriteCategory {
  id: number;
  userId: number;
  name: string;
  icon: string;
  color: string;
  description?: string;
  sortOrder: number;
  itemCount: number;
  createTime: string;
  updateTime: string;
}
```

### 4.3 收藏标签 (FavoriteTag)

```typescript
interface FavoriteTag {
  id: number;
  userId: number;
  name: string;
  useCount: number;
  createTime: string;
  updateTime: string;
}
```

---

## 5. 使用示例

### 5.1 前端集成示例

```javascript
// 添加收藏
async function addToFavorites(messageData) {
  const favoriteData = {
    itemType: 'message',
    title: messageData.content.substring(0, 50),
    content: messageData.content,
    categoryId: 1, // 工作分类
    tags: ['重要'],
    originalData: {
      messageId: messageData.id,
      senderId: messageData.senderId,
      senderName: messageData.senderName
    },
    sourceChat: {
      chatId: messageData.chatId,
      chatName: messageData.chatName,
      chatType: messageData.chatType
    }
  };
  
  const response = await fetch('/api/favorites', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(favoriteData)
  });
  
  return response.json();
}

// 搜索收藏
async function searchFavorites(keyword, filters = {}) {
  const searchParams = {
    keyword,
    page: 1,
    pageSize: 20,
    ...filters
  };
  
  const response = await fetch('/api/favorites/search', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(searchParams)
  });
  
  return response.json();
}
```

### 5.2 批量操作示例

```javascript
// 批量更新分类
async function batchUpdateCategory(favoriteIds, categoryId) {
  const response = await fetch('/api/favorites/batch/category', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({
      ids: favoriteIds,
      categoryId: categoryId
    })
  });
  
  return response.json();
}
```

---

## 6. 注意事项

1. **认证**: 所有接口都需要用户认证，请在请求头中包含有效的认证信息
2. **权限**: 用户只能操作自己的收藏数据
3. **文件大小**: 建议限制单个收藏内容的大小，避免数据库性能问题
4. **标签数量**: 建议每个收藏项的标签数量不超过10个
5. **搜索性能**: 大量数据时建议使用分页和索引优化
6. **数据备份**: 重要收藏数据建议定期备份

---

## 7. 更新日志

### v1.0.0 (2024-01-01)
- 初始版本发布
- 支持基础收藏功能
- 支持分类和标签管理
- 支持高级搜索和批量操作

### 后续版本计划
- 收藏内容的云同步
- 收藏分享功能
- 智能分类推荐
- 全文搜索优化
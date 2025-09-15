# Socket.IO 事件处理实现文档

## 概述

本文档描述了特别关心功能的Socket.IO事件处理实现，包括用户连接管理、实时通知推送、权限验证等功能。

## 核心组件

### 1. AttentionSocketHandler
主要的Socket.IO事件处理器，负责处理所有与特别关心相关的实时事件。

#### 主要功能：
- 用户连接/断开连接管理
- 特别关心更新事件处理
- 用户状态变化事件处理
- 实时通知推送
- 权限验证和安全控制

#### 支持的事件：

**客户端到服务器事件：**
- `connect` - 用户连接事件
- `disconnect` - 用户断开连接事件
- `attentionUpdate` - 特别关心更新事件
- `userStatusChange` - 用户状态变化事件
- `heartbeat` - 心跳检测事件
- `getOnlineUsers` - 获取在线用户列表事件

**服务器到客户端事件：**
- `connected` - 连接成功确认
- `attentionUpdated` - 特别关心更新通知
- `attentionAdded` - 特别关心添加通知
- `attentionUserOnline` - 特别关心用户上线通知
- `attentionUserOffline` - 特别关心用户离线通知
- `attentionUserStatusChange` - 特别关心用户状态变化通知
- `attentionMessage` - 特别关心消息通知
- `onlineUsersList` - 在线用户列表响应
- `heartbeatAck` - 心跳响应
- `attentionUpdateAck` - 特别关心更新确认
- `statusChangeAck` - 状态变化确认
- `error` - 错误消息

### 2. 事件DTO类

#### AttentionUpdateEvent
特别关心更新事件数据传输对象
```java
{
    "userId": "用户ID",
    "targetUserId": "目标用户ID", 
    "eventType": "事件类型(add/remove/update)",
    "data": "事件相关数据"
}
```

#### UserStatusChangeEvent
用户状态变化事件数据传输对象
```java
{
    "userId": "用户ID",
    "status": "新状态(online/offline/away/busy)",
    "previousStatus": "之前状态",
    "timestamp": "时间戳"
}
```

#### AttentionUserOnlineEvent
特别关心用户上线事件数据传输对象
```java
{
    "userId": "用户ID",
    "userName": "用户名",
    "avatar": "头像URL",
    "timestamp": "时间戳"
}
```

#### AttentionUserOfflineEvent
特别关心用户离线事件数据传输对象
```java
{
    "userId": "用户ID",
    "userName": "用户名",
    "avatar": "头像URL",
    "lastActiveTime": "最后活跃时间",
    "timestamp": "时间戳"
}
```

#### AttentionStatusChangeEvent
特别关心用户状态变化事件数据传输对象
```java
{
    "userId": "用户ID",
    "userName": "用户名",
    "avatar": "头像URL",
    "newStatus": "新状态",
    "previousStatus": "之前状态",
    "timestamp": "时间戳"
}
```

#### AttentionMessageEvent
特别关心消息事件数据传输对象
```java
{
    "userId": "用户ID",
    "userName": "用户名",
    "avatar": "头像URL",
    "messagePreview": "消息预览",
    "messageId": "消息ID",
    "timestamp": "时间戳"
}
```

### 3. 配置组件

#### SocketIOConfig
Socket.IO服务器配置类，包含：
- 服务器基本配置（主机名、端口等）
- 心跳检测配置
- 认证配置
- 安全配置

#### SocketIOServerRunner
Socket.IO服务器启动器，负责：
- 服务器启动
- 服务器停止
- 生命周期管理

## 安全特性

### 1. 身份验证
- 连接时验证用户身份
- 支持Token认证
- 用户存在性验证

### 2. 权限控制
- 事件权限验证
- 防止用户操作他人数据
- 房间访问控制

### 3. 数据验证
- 事件数据格式验证
- 参数有效性检查
- 状态值验证

## 性能优化

### 1. 缓存机制
- Redis缓存用户会话信息
- 用户状态缓存
- 减少数据库查询

### 2. 异步处理
- 异步发送通知
- 非阻塞事件处理
- 并发安全

### 3. 房间管理
- 用户房间自动管理
- 精确的消息推送
- 减少不必要的广播

## 错误处理

### 1. 连接错误
- 身份验证失败自动断开
- 连接异常记录和处理
- 优雅的错误响应

### 2. 事件处理错误
- 异常捕获和日志记录
- 错误消息返回客户端
- 确认机制保证消息送达

### 3. 服务异常
- 服务不可用时的降级处理
- 重试机制
- 故障恢复

## 监控和日志

### 1. 连接监控
- 用户连接/断开日志
- 连接数统计
- 异常连接检测

### 2. 事件监控
- 事件处理日志
- 性能指标记录
- 错误率统计

### 3. 业务监控
- 特别关心操作统计
- 通知发送统计
- 用户活跃度监控

## 使用示例

### 客户端连接
```javascript
const socket = io('http://localhost:8082', {
    query: {
        userId: 'user123',
        token: 'jwt_token_here'
    }
});
```

### 发送特别关心更新
```javascript
socket.emit('attentionUpdate', {
    userId: 'user123',
    targetUserId: 'user456',
    eventType: 'add',
    data: null
});
```

### 监听通知
```javascript
socket.on('attentionUserOnline', (data) => {
    console.log('特别关心用户上线:', data);
});

socket.on('attentionMessage', (data) => {
    console.log('收到特别关心消息:', data);
});
```

## 部署注意事项

1. 确保Socket.IO端口（8082）可访问
2. 配置适当的CORS策略
3. 监控服务器资源使用情况
4. 定期清理过期的缓存数据
5. 配置适当的日志级别和轮转策略

## 扩展性考虑

1. 支持集群部署（需要Redis适配器）
2. 支持负载均衡
3. 支持水平扩展
4. 支持多数据中心部署
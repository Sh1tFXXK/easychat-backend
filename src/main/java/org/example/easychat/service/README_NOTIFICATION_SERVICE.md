# 通知服务实现文档

## 概述

本文档描述了EasyChat特别关心功能的通知服务实现，包含消息队列、重试机制、通知模板和个性化设置等完整功能。

## 功能特性

### 1. 核心通知功能
- ✅ 用户上线/离线通知
- ✅ 用户状态变化通知  
- ✅ 消息优先通知
- ✅ 特别关心更新事件

### 2. 消息队列集成
- ✅ RabbitMQ异步处理
- ✅ 高优先级队列支持
- ✅ 死信队列处理
- ✅ 消息持久化

### 3. 重试机制
- ✅ 自动重试失败通知
- ✅ 指数退避策略
- ✅ 最大重试次数限制
- ✅ 定时任务清理

### 4. 通知模板
- ✅ 默认通知模板
- ✅ 自定义模板支持
- ✅ 参数替换功能
- ✅ 模板缓存机制

### 5. 个性化设置
- ✅ 用户通知开关
- ✅ 分类型通知控制
- ✅ 设置持久化
- ✅ 实时生效

### 6. 降级处理
- ✅ 队列失败降级
- ✅ 直接Socket.IO发送
- ✅ 异常恢复机制
- ✅ 服务可用性保障

## 架构设计

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   业务服务层     │───▶│   通知服务层      │───▶│   消息队列层     │
│ (Service Layer) │    │(NotificationSvc) │    │  (RabbitMQ)    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                       ┌──────────────────┐    ┌─────────────────┐
                       │   Socket.IO层    │    │   消息监听器     │
                       │  (Real-time)    │    │  (Listener)    │
                       └──────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                       ┌──────────────────┐    ┌─────────────────┐
                       │     前端客户端    │    │   数据持久化     │
                       │   (Frontend)    │    │  (Database)    │
                       └──────────────────┘    └─────────────────┘
```

## 核心组件

### 1. NotificationService
主要通知服务类，提供以下功能：
- 发送各类通知
- 消息队列集成
- 重试机制管理
- 模板和设置管理

### 2. NotificationMessage
通知消息DTO，包含：
- 通知类型和内容
- 优先级设置
- 重试信息
- 时间戳

### 3. NotificationMessageListener
消息队列监听器，负责：
- 处理队列消息
- 手动确认机制
- 重试策略执行
- 死信队列处理

### 4. RabbitMQConfig
消息队列配置，包含：
- 交换机和队列定义
- 绑定关系配置
- 死信队列设置
- 消息转换器

## 使用示例

### 1. 发送基本通知

```java
@Autowired
private NotificationService notificationService;

// 发送上线通知
notificationService.sendOnlineNotification("user123", "target456");

// 发送消息通知
notificationService.sendMessageNotification("user123", "target456", "Hello!");

// 发送状态变化通知
notificationService.sendStatusChangeNotification("user123", "target456", "busy");
```

### 2. 设置个性化通知

```java
// 更新用户通知设置
Map<String, Boolean> settings = new HashMap<>();
settings.put("online", true);
settings.put("offline", false);
settings.put("message", true);
settings.put("status_change", true);

notificationService.updateUserNotificationSettings("user123", settings);
```

### 3. 自定义通知模板

```java
// 设置自定义模板
notificationService.setNotificationTemplate("online", "🟢 {userName} 现在在线");
notificationService.setNotificationTemplate("message", "💬 {userName}: {messagePreview}");
```

## 配置说明

### 1. RabbitMQ配置

```properties
# RabbitMQ基本配置
spring.rabbitmq.host=127.0.0.1
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# 发布确认
spring.rabbitmq.publisher-confirm-type=correlated
spring.rabbitmq.publisher-returns=true

# 消费者配置
spring.rabbitmq.listener.simple.acknowledge-mode=manual
spring.rabbitmq.listener.simple.concurrency=2
spring.rabbitmq.listener.simple.max-concurrency=10
```

### 2. 队列结构

- **普通队列**: `attention.notification.queue`
- **高优先级队列**: `attention.notification.high.queue`
- **死信队列**: `attention.notification.dlq`
- **交换机**: `attention.notification.exchange`

## 监控和运维

### 1. 关键指标
- 通知发送成功率
- 队列消息积压情况
- 重试次数统计
- 死信消息数量

### 2. 告警机制
- 队列积压告警
- 发送失败率告警
- 死信消息告警
- 服务可用性告警

### 3. 日志记录
- 通知发送日志
- 重试执行日志
- 异常错误日志
- 性能统计日志

## 性能优化

### 1. 消息队列优化
- 合理设置预取数量
- 使用批量确认
- 优化序列化方式
- 设置合适的TTL

### 2. 缓存优化
- 用户设置缓存
- 通知模板缓存
- 用户信息缓存
- Redis连接池优化

### 3. 并发优化
- 异步处理机制
- 线程池配置
- 连接池管理
- 资源复用

## 故障处理

### 1. 常见问题
- RabbitMQ连接失败
- Redis缓存异常
- Socket.IO连接断开
- 数据库操作超时

### 2. 解决方案
- 自动重连机制
- 降级处理策略
- 熔断器保护
- 健康检查

### 3. 恢复流程
- 服务重启恢复
- 消息重新处理
- 数据一致性检查
- 状态同步修复

## 扩展功能

### 1. 推送渠道扩展
- 邮件通知
- 短信通知
- 移动推送
- 微信通知

### 2. 通知类型扩展
- 系统公告
- 活动通知
- 安全提醒
- 业务消息

### 3. 智能化功能
- 通知频率控制
- 智能推送时间
- 用户行为分析
- 个性化推荐

## 测试覆盖

### 1. 单元测试
- 通知发送测试
- 模板渲染测试
- 设置管理测试
- 重试机制测试

### 2. 集成测试
- 消息队列集成
- Socket.IO集成
- 数据库集成
- 缓存集成

### 3. 性能测试
- 高并发发送测试
- 内存使用测试
- 响应时间测试
- 吞吐量测试

## 版本历史

- **v1.0.0**: 基础通知功能实现
- **v1.1.0**: 消息队列集成
- **v1.2.0**: 重试机制和模板支持
- **v1.3.0**: 个性化设置和降级处理

## 相关文档

- [特别关心功能需求文档](../../../specs/backend-special-attention/requirements.md)
- [特别关心功能设计文档](../../../specs/backend-special-attention/design.md)
- [Socket.IO集成文档](../../Handler/README_SOCKET_IO.md)
- [API接口文档](../../controller/README_API.md)
# 错误处理和异常管理系统

## 概述

本系统为EasyChat特别关心功能提供了完整的错误处理和异常管理机制，包括：

- 统一的业务异常处理
- 全局异常拦截和转换
- 错误监控和统计
- 熔断器和降级处理
- 自动化错误日志记录
- 性能监控和告警

## 核心组件

### 1. BusinessException - 业务异常类

```java
// 基本用法
throw new BusinessException(ErrorCode.ATTENTION_ALREADY_EXISTS);

// 自定义消息
throw new BusinessException(ErrorCode.ATTENTION_ALREADY_EXISTS, "用户123已经关心了用户456");

// 静态工厂方法
throw BusinessException.attentionError(ErrorCode.CANNOT_ATTENTION_SELF);
throw BusinessException.paramError("用户ID不能为空");
throw BusinessException.systemError("系统异常", cause);
```

### 2. ErrorCode - 错误码枚举

定义了所有特别关心功能相关的错误码：

- **4001-4099**: 特别关心业务错误
- **4101-4199**: 用户状态相关错误
- **4201-4299**: 通知相关错误
- **4301-4399**: 批量操作相关错误
- **5001-5099**: 数据库相关错误
- **5101-5199**: 缓存相关错误
- **6001-6099**: Socket.IO相关错误
- **7001-7099**: 异步处理相关错误

### 3. GlobalExceptionHandler - 全局异常处理器

自动拦截和处理所有异常：

```java
@ExceptionHandler(BusinessException.class)
public ApiResponseBO<Object> handleBusinessException(BusinessException e, HttpServletRequest request)

@ExceptionHandler(MethodArgumentNotValidException.class)
public ApiResponseBO<Object> handleMethodArgumentNotValidException(...)

@ExceptionHandler(DataAccessException.class)
public ApiResponseBO<Object> handleDataAccessException(...)
```

### 4. ErrorHandlingUtil - 错误处理工具类

提供便捷的错误处理和参数验证方法：

```java
// 安全执行操作
String result = ErrorHandlingUtil.safeExecute(() -> someOperation(), "操作名称");

// 参数验证
ErrorHandlingUtil.validateNotBlank(userId, "用户ID");
ErrorHandlingUtil.validateRange(pageSize, 1, 100, "页面大小");

// 特别关心专用验证
ErrorHandlingUtil.AttentionValidation.validateUserId(userId);
ErrorHandlingUtil.AttentionValidation.validateNotSelf(userId, targetUserId);
```

### 5. ErrorMonitorService - 错误监控服务

实时监控和统计错误信息：

```java
// 记录错误
errorMonitorService.recordError("BUSINESS_ERROR", 4001, "/api/attention");

// 获取统计信息
Map<String, Object> stats = errorMonitorService.getErrorStatistics("hour");
```

### 6. CircuitBreakerService - 熔断器服务

提供服务降级和熔断保护：

```java
// 执行带熔断保护的操作
T result = circuitBreakerService.executeWithCircuitBreaker(
    "SpecialAttentionService",
    () -> actualOperation(),
    () -> fallbackOperation()
);
```

### 7. ErrorHandlingAspect - 错误处理切面

自动拦截方法执行，记录错误和性能指标：

- 监控Service层方法执行
- 监控Controller层方法执行
- 监控数据库操作性能
- 监控缓存操作性能

## 使用指南

### 1. 在Service层抛出业务异常

```java
@Service
public class SpecialAttentionService {
    
    public void addAttention(String userId, String targetUserId) {
        // 参数验证
        ErrorHandlingUtil.AttentionValidation.validateUserId(userId);
        ErrorHandlingUtil.AttentionValidation.validateNotSelf(userId, targetUserId);
        
        // 业务逻辑检查
        if (attentionExists(userId, targetUserId)) {
            throw new BusinessException(ErrorCode.ATTENTION_ALREADY_EXISTS);
        }
        
        // 执行业务操作
        try {
            // ... 业务逻辑
        } catch (Exception e) {
            throw ErrorHandlingUtil.convertException(e, "添加特别关心");
        }
    }
}
```

### 2. 在Controller层处理响应

```java
@RestController
public class SpecialAttentionController {
    
    @PostMapping("/attention")
    public ApiResponseBO<SpecialAttentionVO> addAttention(@RequestBody AttentionAddDTO dto) {
        // 业务异常会被GlobalExceptionHandler自动处理
        SpecialAttentionVO result = attentionService.addAttention(dto);
        return new ApiResponseBO<>(true, "添加成功", 200, result);
    }
}
```

### 3. 使用熔断器保护关键操作

```java
@Service
public class NotificationService {
    
    public void sendNotification(String userId, String message) {
        circuitBreakerService.executeWithCircuitBreaker(
            "NotificationService",
            () -> {
                // 实际发送通知的逻辑
                return actualSendNotification(userId, message);
            },
            () -> {
                // 降级处理：记录到队列稍后重试
                log.warn("通知发送降级，记录到重试队列");
                return null;
            }
        );
    }
}
```

## 监控和告警

### 1. 错误统计API

```bash
# 获取小时级别错误统计
GET /admin/error-monitor/statistics?timeRange=hour

# 获取日级别错误统计
GET /admin/error-monitor/statistics?timeRange=day
```

### 2. 系统健康检查

```bash
# 获取系统健康状态
GET /admin/error-monitor/health

# 获取熔断器状态
GET /admin/error-monitor/circuit-breaker
```

### 3. 手动操作

```bash
# 重置熔断器
POST /admin/error-monitor/circuit-breaker/reset?serviceName=SpecialAttentionService

# 清理过期数据
POST /admin/error-monitor/cleanup
```

## 日志配置

系统使用logback进行日志管理，配置了多个日志文件：

- `easychat.log`: 应用主日志
- `error.log`: 错误日志
- `attention.log`: 特别关心功能专用日志
- `business-error.log`: 业务异常日志
- `performance.log`: 性能监控日志

## 定时任务

### 1. 错误数据清理

每天凌晨2点自动清理过期的错误统计数据。

### 2. 错误统计报告

- 每小时生成错误统计报告
- 每天生成日错误统计报告

## 告警机制

系统会在以下情况触发告警：

1. **错误数量告警**: 当某类错误在1小时内超过阈值时
2. **错误率告警**: 当某类错误的错误率超过阈值时
3. **熔断器告警**: 当服务熔断器开启时
4. **性能告警**: 当操作执行时间超过阈值时

## 最佳实践

### 1. 异常处理原则

- 在Service层抛出具体的业务异常
- 使用ErrorHandlingUtil进行参数验证
- 不要在Controller层处理业务异常
- 使用熔断器保护外部依赖

### 2. 错误码设计

- 使用有意义的错误码和错误消息
- 错误码按功能模块分组
- 错误消息要对用户友好

### 3. 日志记录

- 业务异常记录为WARN级别
- 系统异常记录为ERROR级别
- 包含足够的上下文信息
- 避免记录敏感信息

### 4. 监控和告警

- 定期检查错误统计数据
- 及时处理告警信息
- 根据业务需求调整阈值
- 定期清理过期数据

## 扩展指南

### 1. 添加新的错误码

在`ErrorCode`枚举中添加新的错误码：

```java
NEW_ERROR_TYPE(4999, "新的错误类型");
```

### 2. 自定义异常处理

在`GlobalExceptionHandler`中添加新的异常处理方法：

```java
@ExceptionHandler(CustomException.class)
public ApiResponseBO<Object> handleCustomException(CustomException e) {
    // 处理逻辑
}
```

### 3. 扩展监控指标

在`ErrorMonitorService`中添加新的监控指标：

```java
public void recordCustomMetric(String metricName, Object value) {
    // 记录自定义指标
}
```

### 4. 自定义降级策略

在`CircuitBreakerService`中添加新的降级方法：

```java
public static class CustomFallback {
    public static Object customFallbackMethod() {
        // 自定义降级逻辑
    }
}
```
package org.example.easychat.Handler;

import lombok.Getter;

/**
 * 错误码枚举
 * 定义特别关心功能相关的错误码
 */
@Getter
public enum ErrorCode {
    // 通用错误码
    INVALID_PARAMS(400, "参数无效"),
    UNAUTHORIZED(401, "未授权访问"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),

    // 特别关心相关错误码
    ATTENTION_ALREADY_EXISTS(4001, "特别关心关系已存在"),
    ATTENTION_NOT_FOUND(4002, "特别关心关系不存在"),
    CANNOT_ATTENTION_SELF(4003, "不能关心自己"),
    TARGET_USER_NOT_FOUND(4004, "目标用户不存在"),
    ATTENTION_LIMIT_EXCEEDED(4005, "特别关心数量超出限制"),
    
    // 用户状态相关错误码
    USER_STATUS_UPDATE_FAILED(4101, "用户状态更新失败"),
    USER_OFFLINE(4102, "用户已离线"),
    
    // 通知相关错误码
    NOTIFICATION_SEND_FAILED(4201, "通知发送失败"),
    NOTIFICATION_SETTINGS_INVALID(4202, "通知设置无效"),
    
    // 批量操作相关错误码
    BATCH_OPERATION_FAILED(4301, "批量操作失败"),
    BATCH_SIZE_EXCEEDED(4302, "批量操作数量超出限制"),
    
    // 数据库相关错误码
    DATABASE_ERROR(5001, "数据库操作失败"),
    DATA_INTEGRITY_ERROR(5002, "数据完整性错误"),
    CONCURRENT_UPDATE_ERROR(5003, "并发更新冲突"),
    
    // 限流相关错误码
    RATE_LIMIT_EXCEEDED(4291, "请求频率超出限制"),
    
    // 缓存相关错误码
    CACHE_ERROR(5101, "缓存操作失败"),
    
    // Socket.IO相关错误码
    SOCKET_CONNECTION_ERROR(6001, "Socket连接异常"),
    SOCKET_EVENT_ERROR(6002, "Socket事件处理失败"),
    SOCKET_PERMISSION_DENIED(6003, "Socket权限不足"),
    
    // 异步处理相关错误码
    ASYNC_TASK_ERROR(7001, "异步任务执行失败"),
    THREAD_POOL_ERROR(7002, "线程池异常"),
    
    // 配置相关错误码
    CONFIG_ERROR(8001, "配置错误"),
    ENVIRONMENT_ERROR(8002, "环境异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
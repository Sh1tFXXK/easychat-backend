package org.example.easychat.Entity;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // 通用错误
    SUCCESS(200, "成功"),
    INVALID_PARAMS(400001, "参数错误"),
    UNAUTHORIZED(401001, "未授权"),
    FORBIDDEN(403001, "权限不足"),
    NOT_FOUND(404001, "资源不存在"),
    INTERNAL_ERROR(500001, "内部服务器错误"),
    
    // 特别关心相关错误
    CANNOT_ATTENTION_SELF(400101, "不能关心自己"),
    ATTENTION_ALREADY_EXISTS(400102, "已经在特别关心列表中"),
    ATTENTION_LIMIT_EXCEEDED(400103, "特别关心数量超限"),
    TARGET_USER_NOT_FOUND(404101, "目标用户不存在"),
    ATTENTION_NOT_FOUND(404102, "特别关心记录不存在"),
    ATTENTION_BLOCKED(403101, "用户不允许被特别关心"),
    
    // 系统错误
    DATABASE_ERROR(500002, "数据库错误"),
    NETWORK_ERROR(500003, "网络错误"),
    CACHE_ERROR(500004, "缓存错误");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    

}
package org.example.easychat.Handler;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 业务异常类
 * 用于处理特别关心功能相关的业务异常
 */
@Getter
@Slf4j
public class BusinessException extends RuntimeException {
    private final int code;
    private final String message;
    private final String errorType;
    private final Object data;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
        this.message = message;
        this.errorType = "BUSINESS_ERROR";
        this.data = null;
        logException();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
        this.errorType = "BUSINESS_ERROR";
        this.data = null;
        logException();
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.errorType = "BUSINESS_ERROR";
        this.data = null;
        logException();
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.code = errorCode.getCode();
        this.message = customMessage;
        this.errorType = "BUSINESS_ERROR";
        this.data = null;
        logException();
    }

    public BusinessException(ErrorCode errorCode, String customMessage, Object data) {
        super(customMessage);
        this.code = errorCode.getCode();
        this.message = customMessage;
        this.errorType = "BUSINESS_ERROR";
        this.data = data;
        logException();
    }

    public BusinessException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.code = errorCode.getCode();
        this.message = customMessage;
        this.errorType = "BUSINESS_ERROR";
        this.data = null;
        logException();
    }

    public BusinessException(ErrorCode errorCode, String customMessage, Object data, Throwable cause) {
        super(customMessage, cause);
        this.code = errorCode.getCode();
        this.message = customMessage;
        this.errorType = "BUSINESS_ERROR";
        this.data = data;
        logException();
    }

    /**
     * 记录异常日志
     */
    private void logException() {
        if (code >= 500) {
            log.error("业务异常 - 代码: {}, 消息: {}, 类型: {}", code, message, errorType, this);
        } else {
            log.warn("业务异常 - 代码: {}, 消息: {}, 类型: {}", code, message, errorType);
        }
    }

    /**
     * 创建特别关心相关的业务异常
     */
    public static BusinessException attentionError(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException attentionError(ErrorCode errorCode, String customMessage) {
        return new BusinessException(errorCode, customMessage);
    }

    public static BusinessException attentionError(ErrorCode errorCode, String customMessage, Object data) {
        return new BusinessException(errorCode, customMessage, data);
    }

    /**
     * 创建系统级异常
     */
    public static BusinessException systemError(String message) {
        return new BusinessException(ErrorCode.INTERNAL_ERROR, message);
    }

    public static BusinessException systemError(String message, Throwable cause) {
        return new BusinessException(ErrorCode.INTERNAL_ERROR, message, cause);
    }

    /**
     * 创建参数验证异常
     */
    public static BusinessException paramError(String message) {
        return new BusinessException(ErrorCode.INVALID_PARAMS, message);
    }

    public static BusinessException paramError(String message, Object data) {
        return new BusinessException(ErrorCode.INVALID_PARAMS, message, data);
    }
}
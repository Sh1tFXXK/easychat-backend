package org.example.easychat.Exception;

/**
 * 业务异常类
 */
public class BusinessException extends RuntimeException {
    
    private int code;
    private String errorType;
    private Object data;
    
    public BusinessException(String message) {
        super(message);
        this.code = 500;
        this.errorType = "SYSTEM_ERROR";
    }
    
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.errorType = code >= 500 ? "SYSTEM_ERROR" : "BUSINESS_ERROR";
    }
    
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
        this.errorType = "SYSTEM_ERROR";
    }
    
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.errorType = code >= 500 ? "SYSTEM_ERROR" : "BUSINESS_ERROR";
    }
    
    public BusinessException(int code) {
        super("业务异常");
        this.code = code;
        this.errorType = "BUSINESS_ERROR";
    }
    
    public int getCode() {
        return code;
    }
    
    public void setCode(int code) {
        this.code = code;
    }
    
    public String getErrorType() {
        return errorType;
    }
    
    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
    
    /**
     * 创建参数错误异常
     */
    public static BusinessException paramError(String message) {
        return new BusinessException(400, message);
    }
    
    /**
     * 创建系统错误异常
     */
    public static BusinessException systemError(String message) {
        return new BusinessException(500, message);
    }
    
    /**
     * 创建系统错误异常（带原因）
     */
    public static BusinessException systemError(String message, Throwable cause) {
        return new BusinessException(500, message, cause);
    }
}
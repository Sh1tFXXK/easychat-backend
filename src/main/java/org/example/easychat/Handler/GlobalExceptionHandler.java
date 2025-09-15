package org.example.easychat.Handler;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.BO.ApiResponseBO;
import org.example.easychat.service.ErrorMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理特别关心功能相关的异常和系统异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired(required = false)
    private ErrorMonitorService errorMonitorService;

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponseBO<Object> handleBusinessException(BusinessException e, HttpServletRequest request) {
        logError("业务异常", e, request);
        
        // 记录错误监控
        recordErrorMetrics(e, request);
        
        HttpStatus status = getHttpStatusFromCode(e.getCode());
        
        return new ApiResponseBO<>(
            false, 
            e.getMessage(), 
            e.getCode(), 
            buildErrorData(e)
        );
    }

    /**
     * 处理 @RequestBody 参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponseBO<Object> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        
        Map<String, String> fieldErrors = new HashMap<>();
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .peek(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()))
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        
        logError("参数验证异常", e, request);
        recordErrorMetrics("VALIDATION_ERROR", request);
        
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("fieldErrors", fieldErrors);
        errorData.put("errorType", "VALIDATION_ERROR");
        
        return new ApiResponseBO<>(false, "参数验证失败: " + errorMessage, 400, errorData);
    }

    /**
     * 处理 @RequestParam 和 @PathVariable 参数验证异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponseBO<Object> handleConstraintViolationException(
            ConstraintViolationException e, HttpServletRequest request) {
        
        String errorMessage = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        
        logError("约束验证异常", e, request);
        recordErrorMetrics("CONSTRAINT_VIOLATION", request);
        
        return new ApiResponseBO<>(false, "参数约束验证失败: " + errorMessage, 400, null);
    }

    /**
     * 处理表单绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponseBO<Object> handleBindException(BindException e, HttpServletRequest request) {
        String errorMessage = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        
        logError("表单绑定异常", e, request);
        recordErrorMetrics("BIND_ERROR", request);
        
        return new ApiResponseBO<>(false, "表单绑定失败: " + errorMessage, 400, null);
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponseBO<Object> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        
        logError("缺少请求参数", e, request);
        recordErrorMetrics("MISSING_PARAMETER", request);
        
        return new ApiResponseBO<>(false, "缺少必需参数: " + e.getParameterName(), 400, null);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponseBO<Object> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        
        logError("参数类型不匹配", e, request);
        recordErrorMetrics("TYPE_MISMATCH", request);
        
        return new ApiResponseBO<>(false, 
            String.format("参数 '%s' 类型不匹配，期望类型: %s", 
                e.getName(), e.getRequiredType().getSimpleName()), 400, null);
    }

    /**
     * 处理HTTP请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponseBO<Object> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        
        logError("HTTP方法不支持", e, request);
        recordErrorMetrics("METHOD_NOT_ALLOWED", request);
        
        return new ApiResponseBO<>(false, 
            String.format("不支持的HTTP方法: %s，支持的方法: %s", 
                e.getMethod(), String.join(", ", e.getSupportedMethods())), 405, null);
    }

    /**
     * 处理HTTP消息不可读异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponseBO<Object> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        
        logError("HTTP消息不可读", e, request);
        recordErrorMetrics("MESSAGE_NOT_READABLE", request);
        
        return new ApiResponseBO<>(false, "请求体格式错误或不可读", 400, null);
    }

    /**
     * 处理数据库相关异常
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponseBO<Object> handleDataAccessException(DataAccessException e, HttpServletRequest request) {
        logError("数据库访问异常", e, request);
        recordErrorMetrics("DATABASE_ERROR", request);
        
        // 根据具体异常类型返回不同错误信息
        if (e instanceof DuplicateKeyException) {
            return new ApiResponseBO<>(false, "数据重复，操作失败", 5002, null);
        } else if (e instanceof DataIntegrityViolationException) {
            return new ApiResponseBO<>(false, "数据完整性约束违反", 5002, null);
        }
        
        return new ApiResponseBO<>(false, "数据库操作失败", 5001, null);
    }

    /**
     * 处理SQL异常
     */
    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponseBO<Object> handleSQLException(SQLException e, HttpServletRequest request) {
        logError("SQL异常", e, request);
        recordErrorMetrics("SQL_ERROR", request);
        
        return new ApiResponseBO<>(false, "数据库操作异常", 5001, null);
    }

    /**
     * 处理异步任务异常
     */
    @ExceptionHandler(CompletionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponseBO<Object> handleCompletionException(CompletionException e, HttpServletRequest request) {
        Throwable cause = e.getCause();
        
        // 如果是业务异常，则按业务异常处理
        if (cause instanceof BusinessException) {
            return handleBusinessException((BusinessException) cause, request);
        }
        
        logError("异步任务异常", e, request);
        recordErrorMetrics("ASYNC_ERROR", request);
        
        return new ApiResponseBO<>(false, "异步任务执行失败", 500, null);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponseBO<Object> handleIllegalArgumentException(
            IllegalArgumentException e, HttpServletRequest request) {
        
        logError("非法参数异常", e, request);
        recordErrorMetrics("ILLEGAL_ARGUMENT", request);
        
        return new ApiResponseBO<>(false, "参数错误: " + e.getMessage(), 400, null);
    }

    /**
     * 处理非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponseBO<Object> handleIllegalStateException(
            IllegalStateException e, HttpServletRequest request) {
        
        logError("非法状态异常", e, request);
        recordErrorMetrics("ILLEGAL_STATE", request);
        
        return new ApiResponseBO<>(false, "系统状态异常: " + e.getMessage(), 500, null);
    }

    /**
     * 处理其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponseBO<Object> handleException(Exception e, HttpServletRequest request) {
        logError("未知异常", e, request);
        recordErrorMetrics("UNKNOWN_ERROR", request);
        
        // 生产环境不暴露具体错误信息
        String message = isProductionEnvironment() ? "系统内部错误" : "系统内部错误: " + e.getMessage();
        
        return new ApiResponseBO<>(false, message, 500, null);
    }

    /**
     * 记录错误日志
     */
    private void logError(String errorType, Exception e, HttpServletRequest request) {
        String requestInfo = String.format("请求信息 - URI: %s, 方法: %s, IP: %s, 用户代理: %s",
            request.getRequestURI(),
            request.getMethod(),
            getClientIpAddress(request),
            request.getHeader("User-Agent"));
        
        if (e instanceof BusinessException) {
            BusinessException be = (BusinessException) e;
            if (be.getCode() >= 500) {
                log.error("{} - {} - {}", errorType, requestInfo, be.getMessage(), e);
            } else {
                log.warn("{} - {} - {}", errorType, requestInfo, be.getMessage());
            }
        } else {
            log.error("{} - {} - {}", errorType, requestInfo, e.getMessage(), e);
        }
    }

    /**
     * 记录错误监控指标
     */
    private void recordErrorMetrics(BusinessException e, HttpServletRequest request) {
        if (errorMonitorService != null) {
            errorMonitorService.recordError(e.getErrorType(), e.getCode(), request.getRequestURI());
        }
    }

    private void recordErrorMetrics(String errorType, HttpServletRequest request) {
        if (errorMonitorService != null) {
            errorMonitorService.recordError(errorType, 400, request.getRequestURI());
        }
    }

    /**
     * 根据错误码获取HTTP状态
     */
    private HttpStatus getHttpStatusFromCode(int code) {
        if (code >= 500) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        } else if (code == 404) {
            return HttpStatus.NOT_FOUND;
        } else if (code == 403) {
            return HttpStatus.FORBIDDEN;
        } else if (code == 401) {
            return HttpStatus.UNAUTHORIZED;
        } else {
            return HttpStatus.BAD_REQUEST;
        }
    }

    /**
     * 构建错误数据
     */
    private Map<String, Object> buildErrorData(BusinessException e) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("errorType", e.getErrorType());
        errorData.put("timestamp", LocalDateTime.now().toString());
        
        if (e.getData() != null) {
            errorData.put("data", e.getData());
        }
        
        return errorData;
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * 判断是否为生产环境
     */
    private boolean isProductionEnvironment() {
        // 这里可以根据实际情况判断环境
        return false; // 暂时返回false，可以通过配置文件或环境变量来判断
    }
}
package org.example.easychat.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.example.easychat.Handler.BusinessException;
import org.example.easychat.service.ErrorMonitorService;
import org.example.easychat.utils.ErrorHandlingUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 错误处理切面
 * 自动处理特别关心功能相关的异常和错误监控
 */
@Slf4j
@Aspect
@Component
public class ErrorHandlingAspect {

    @Autowired
    private ErrorMonitorService errorMonitorService;

    /**
     * 定义切点：特别关心相关的Service方法
     */
    @Pointcut("execution(* org.example.easychat.service.SpecialAttentionService.*(..))")
    public void attentionServiceMethods() {}

    @Pointcut("execution(* org.example.easychat.service.UserStatusService.*(..))")
    public void userStatusServiceMethods() {}

    @Pointcut("execution(* org.example.easychat.service.NotificationService.*(..))")
    public void notificationServiceMethods() {}

    /**
     * 定义切点：特别关心相关的Controller方法
     */
    @Pointcut("execution(* org.example.easychat.controller.SpecialAttentionController.*(..))")
    public void attentionControllerMethods() {}

    /**
     * 定义切点：Socket.IO处理器方法
     */
    @Pointcut("execution(* org.example.easychat.Handler.AttentionSocketHandler.*(..))")
    public void socketHandlerMethods() {}

    /**
     * 组合切点：所有特别关心相关的方法
     */
    @Pointcut("attentionServiceMethods() || userStatusServiceMethods() || notificationServiceMethods() || attentionControllerMethods() || socketHandlerMethods()")
    public void allAttentionMethods() {}

    /**
     * 环绕通知：处理异常和监控
     */
    @Around("allAttentionMethods()")
    public Object handleErrorsAndMonitoring(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();
        
        try {
            // 执行目标方法
            Object result = joinPoint.proceed();
            
            // 记录成功执行的性能指标
            long executionTime = System.currentTimeMillis() - startTime;
            recordSuccessMetrics(methodName, executionTime);
            
            return result;
            
        } catch (BusinessException e) {
            // 业务异常处理
            handleBusinessException(e, methodName);
            throw e;
            
        } catch (Exception e) {
            // 其他异常转换为业务异常
            BusinessException businessException = ErrorHandlingUtil.convertException(e, methodName);
            handleBusinessException(businessException, methodName);
            throw businessException;
        }
    }

    /**
     * 处理业务异常
     */
    private void handleBusinessException(BusinessException e, String methodName) {
        // 记录错误监控指标
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            errorMonitorService.recordError(e.getErrorType(), e.getCode(), request.getRequestURI());
        } else {
            errorMonitorService.recordError(e.getErrorType(), e.getCode(), methodName);
        }
        
        // 记录详细的错误日志
        if (e.getCode() >= 500) {
            log.error("系统异常 - 方法: {}, 错误码: {}, 消息: {}", methodName, e.getCode(), e.getMessage(), e);
        } else {
            log.warn("业务异常 - 方法: {}, 错误码: {}, 消息: {}", methodName, e.getCode(), e.getMessage());
        }
    }

    /**
     * 记录成功执行的指标
     */
    private void recordSuccessMetrics(String methodName, long executionTime) {
        // 记录性能指标
        if (executionTime > 1000) { // 超过1秒的慢查询
            log.warn("慢操作警告 - 方法: {}, 执行时间: {}ms", methodName, executionTime);
        } else if (executionTime > 500) { // 超过500ms的操作
            log.info("操作耗时 - 方法: {}, 执行时间: {}ms", methodName, executionTime);
        }
    }

    /**
     * 获取当前HTTP请求
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 定义切点：数据库操作方法
     */
    @Pointcut("execution(* org.example.easychat.Mapper.SpecialAttentionMapper.*(..))")
    public void attentionMapperMethods() {}

    @Pointcut("execution(* org.example.easychat.Mapper.UserStatusMapper.*(..))")
    public void userStatusMapperMethods() {}

    @Pointcut("execution(* org.example.easychat.Mapper.AttentionNotificationMapper.*(..))")
    public void notificationMapperMethods() {}

    /**
     * 组合切点：所有数据库操作方法
     */
    @Pointcut("attentionMapperMethods() || userStatusMapperMethods() || notificationMapperMethods()")
    public void allMapperMethods() {}

    /**
     * 环绕通知：数据库操作监控
     */
    @Around("allMapperMethods()")
    public Object monitorDatabaseOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 记录数据库操作性能
            if (executionTime > 2000) { // 超过2秒的数据库操作
                log.error("数据库慢查询 - 方法: {}, 执行时间: {}ms", methodName, executionTime);
            } else if (executionTime > 1000) { // 超过1秒的数据库操作
                log.warn("数据库操作耗时 - 方法: {}, 执行时间: {}ms", methodName, executionTime);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("数据库操作异常 - 方法: {}, 错误: {}", methodName, e.getMessage(), e);
            
            // 记录数据库错误
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                errorMonitorService.recordError("DATABASE_ERROR", 5001, request.getRequestURI());
            } else {
                errorMonitorService.recordError("DATABASE_ERROR", 5001, methodName);
            }
            
            throw e;
        }
    }

    /**
     * 定义切点：缓存操作方法
     */
    @Pointcut("execution(* org.example.easychat.service.CacheService.*(..))")
    public void cacheServiceMethods() {}

    /**
     * 环绕通知：缓存操作监控
     */
    @Around("cacheServiceMethods()")
    public Object monitorCacheOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 记录缓存操作性能
            if (executionTime > 500) { // 超过500ms的缓存操作
                log.warn("缓存操作耗时 - 方法: {}, 执行时间: {}ms", methodName, executionTime);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("缓存操作异常 - 方法: {}, 错误: {}", methodName, e.getMessage(), e);
            
            // 记录缓存错误，但不影响主流程
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                errorMonitorService.recordError("CACHE_ERROR", 5101, request.getRequestURI());
            } else {
                errorMonitorService.recordError("CACHE_ERROR", 5101, methodName);
            }
            
            // 缓存异常通常不应该中断主流程，可以考虑返回null或默认值
            // 这里根据具体业务需求决定是否重新抛出异常
            throw e;
        }
    }
}
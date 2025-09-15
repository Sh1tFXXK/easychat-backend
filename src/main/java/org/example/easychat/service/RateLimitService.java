package org.example.easychat.service;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.Config.RateLimitConfig;
import org.example.easychat.Handler.ErrorCode;
import org.example.easychat.Handler.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 限流服务
 */
@Slf4j
@Service
public class RateLimitService {
    
    @Autowired
    private RateLimitConfig rateLimitConfig;
    
    /**
     * 检查添加特别关心限流
     */
    public void checkAddAttentionRateLimit(String userId) {
        String key = rateLimitConfig.getRateLimitKey(
            RateLimitConfig.RateLimitConstants.API_ADD_ATTENTION, userId);
        
        long remaining = rateLimitConfig.checkRateLimit(
            key,
            RateLimitConfig.RateLimitConstants.ADD_ATTENTION_WINDOW,
            RateLimitConfig.RateLimitConstants.ADD_ATTENTION_LIMIT
        );
        
        if (remaining < 0) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
                "添加特别关心操作过于频繁，请稍后再试");
        }
        
        log.debug("添加特别关心限流检查通过: userId={}, remaining={}", userId, remaining);
    }
    
    /**
     * 检查删除特别关心限流
     */
    public void checkRemoveAttentionRateLimit(String userId) {
        String key = rateLimitConfig.getRateLimitKey(
            RateLimitConfig.RateLimitConstants.API_REMOVE_ATTENTION, userId);
        
        long remaining = rateLimitConfig.checkRateLimit(
            key,
            RateLimitConfig.RateLimitConstants.REMOVE_ATTENTION_WINDOW,
            RateLimitConfig.RateLimitConstants.REMOVE_ATTENTION_LIMIT
        );
        
        if (remaining < 0) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, 
                "删除特别关心操作过于频繁，请稍后再试");
        }
        
        log.debug("删除特别关心限流检查通过: userId={}, remaining={}", userId, remaining);
    }
    
    /**
     * 检查更新特别关心限流
     */
    public void checkUpdateAttentionRateLimit(String userId) {
        String key = rateLimitConfig.getRateLimitKey(
            RateLimitConfig.RateLimitConstants.API_UPDATE_ATTENTION, userId);
        
        long remaining = rateLimitConfig.checkRateLimit(
            key,
            RateLimitConfig.RateLimitConstants.UPDATE_ATTENTION_WINDOW,
            RateLimitConfig.RateLimitConstants.UPDATE_ATTENTION_LIMIT
        );
        
        if (remaining < 0) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, 
                "更新特别关心设置过于频繁，请稍后再试");
        }
        
        log.debug("更新特别关心限流检查通过: userId={}, remaining={}", userId, remaining);
    }
    
    /**
     * 检查批量操作限流
     */
    public void checkBatchOperationRateLimit(String userId) {
        String key = rateLimitConfig.getRateLimitKey(
            RateLimitConfig.RateLimitConstants.API_BATCH_OPERATION, userId);
        
        long remaining = rateLimitConfig.checkRateLimit(
            key,
            RateLimitConfig.RateLimitConstants.BATCH_OPERATION_WINDOW,
            RateLimitConfig.RateLimitConstants.BATCH_OPERATION_LIMIT
        );
        
        if (remaining < 0) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, 
                "批量操作过于频繁，请稍后再试");
        }
        
        log.debug("批量操作限流检查通过: userId={}, remaining={}", userId, remaining);
    }
    
    /**
     * 检查查询特别关心限流
     */
    public void checkQueryAttentionRateLimit(String userId) {
        String key = rateLimitConfig.getRateLimitKey(
            RateLimitConfig.RateLimitConstants.API_QUERY_ATTENTION, userId);
        
        long remaining = rateLimitConfig.checkRateLimit(
            key,
            RateLimitConfig.RateLimitConstants.QUERY_ATTENTION_WINDOW,
            RateLimitConfig.RateLimitConstants.QUERY_ATTENTION_LIMIT
        );
        
        if (remaining < 0) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, 
                "查询操作过于频繁，请稍后再试");
        }
        
        log.debug("查询特别关心限流检查通过: userId={}, remaining={}", userId, remaining);
    }
    
    /**
     * 检查Socket.IO事件限流
     */
    public void checkSocketEventRateLimit(String userId, String eventType) {
        String key = rateLimitConfig.getRateLimitKey(eventType, userId);
        
        long remaining = rateLimitConfig.checkRateLimit(
            key,
            RateLimitConfig.RateLimitConstants.SOCKET_EVENT_WINDOW,
            RateLimitConfig.RateLimitConstants.SOCKET_EVENT_LIMIT
        );
        
        if (remaining < 0) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, 
                "Socket事件发送过于频繁，请稍后再试");
        }
        
        log.debug("Socket事件限流检查通过: userId={}, eventType={}, remaining={}", 
                userId, eventType, remaining);
    }
    
    /**
     * 检查IP限流（防止恶意攻击）
     */
    public void checkIpRateLimit(String ip, String operation) {
        String key = rateLimitConfig.getRateLimitKey("ip:" + operation, ip);
        
        // IP限流更严格：1分钟内最多50次请求
        long remaining = rateLimitConfig.checkRateLimit(key, 60, 50);
        
        if (remaining < 0) {
            log.warn("IP限流触发: ip={}, operation={}", ip, operation);
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, 
                "请求过于频繁，请稍后再试");
        }
        
        log.debug("IP限流检查通过: ip={}, operation={}, remaining={}", ip, operation, remaining);
    }
}
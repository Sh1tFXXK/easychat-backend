package org.example.easychat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 熔断器服务
 * 用于处理特别关心功能的降级和熔断逻辑
 */
@Slf4j
@Service
public class CircuitBreakerService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String CIRCUIT_BREAKER_KEY = "circuit:breaker:";
    private static final String FAILURE_COUNT_KEY = "failure:count:";
    private static final String LAST_FAILURE_TIME_KEY = "last:failure:";

    // 熔断器状态
    public enum CircuitState {
        CLOSED,    // 关闭状态，正常处理请求
        OPEN,      // 开启状态，拒绝请求
        HALF_OPEN  // 半开状态，允许少量请求测试服务是否恢复
    }

    // 熔断器配置
    private static final int FAILURE_THRESHOLD = 5;        // 失败阈值
    private static final long TIMEOUT_DURATION = 60000;    // 熔断超时时间（毫秒）
    private static final long RECOVERY_TIMEOUT = 30000;    // 恢复超时时间（毫秒）

    /**
     * 执行带熔断保护的操作
     */
    public <T> T executeWithCircuitBreaker(String serviceName, Supplier<T> operation, Supplier<T> fallback) {
        CircuitState state = getCircuitState(serviceName);
        
        switch (state) {
            case OPEN:
                // 熔断器开启，检查是否可以进入半开状态
                if (canAttemptReset(serviceName)) {
                    setCircuitState(serviceName, CircuitState.HALF_OPEN);
                    return executeHalfOpen(serviceName, operation, fallback);
                } else {
                    log.warn("熔断器开启，执行降级逻辑 - 服务: {}", serviceName);
                    return fallback.get();
                }
                
            case HALF_OPEN:
                return executeHalfOpen(serviceName, operation, fallback);
                
            case CLOSED:
            default:
                return executeClosed(serviceName, operation, fallback);
        }
    }

    /**
     * 在关闭状态下执行操作
     */
    private <T> T executeClosed(String serviceName, Supplier<T> operation, Supplier<T> fallback) {
        try {
            T result = operation.get();
            // 操作成功，重置失败计数
            resetFailureCount(serviceName);
            return result;
        } catch (Exception e) {
            // 操作失败，增加失败计数
            incrementFailureCount(serviceName);
            
            // 检查是否达到失败阈值
            if (getFailureCount(serviceName) >= FAILURE_THRESHOLD) {
                setCircuitState(serviceName, CircuitState.OPEN);
                recordLastFailureTime(serviceName);
                log.error("熔断器开启 - 服务: {}, 失败次数达到阈值: {}", serviceName, FAILURE_THRESHOLD);
            }
            
            log.warn("操作失败，执行降级逻辑 - 服务: {}, 错误: {}", serviceName, e.getMessage());
            return fallback.get();
        }
    }

    /**
     * 在半开状态下执行操作
     */
    private <T> T executeHalfOpen(String serviceName, Supplier<T> operation, Supplier<T> fallback) {
        try {
            T result = operation.get();
            // 操作成功，关闭熔断器
            setCircuitState(serviceName, CircuitState.CLOSED);
            resetFailureCount(serviceName);
            log.info("熔断器关闭 - 服务: {} 恢复正常", serviceName);
            return result;
        } catch (Exception e) {
            // 操作失败，重新开启熔断器
            setCircuitState(serviceName, CircuitState.OPEN);
            recordLastFailureTime(serviceName);
            log.error("熔断器重新开启 - 服务: {} 仍然异常", serviceName);
            return fallback.get();
        }
    }

    /**
     * 获取熔断器状态
     */
    private CircuitState getCircuitState(String serviceName) {
        String key = CIRCUIT_BREAKER_KEY + serviceName;
        String state = (String) redisTemplate.opsForValue().get(key);
        
        if (state == null) {
            return CircuitState.CLOSED;
        }
        
        try {
            return CircuitState.valueOf(state);
        } catch (IllegalArgumentException e) {
            return CircuitState.CLOSED;
        }
    }

    /**
     * 设置熔断器状态
     */
    private void setCircuitState(String serviceName, CircuitState state) {
        String key = CIRCUIT_BREAKER_KEY + serviceName;
        redisTemplate.opsForValue().set(key, state.name(), TIMEOUT_DURATION, TimeUnit.MILLISECONDS);
    }

    /**
     * 获取失败计数
     */
    private int getFailureCount(String serviceName) {
        String key = FAILURE_COUNT_KEY + serviceName;
        Integer count = (Integer) redisTemplate.opsForValue().get(key);
        return count != null ? count : 0;
    }

    /**
     * 增加失败计数
     */
    private void incrementFailureCount(String serviceName) {
        String key = FAILURE_COUNT_KEY + serviceName;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, TIMEOUT_DURATION, TimeUnit.MILLISECONDS);
    }

    /**
     * 重置失败计数
     */
    private void resetFailureCount(String serviceName) {
        String key = FAILURE_COUNT_KEY + serviceName;
        redisTemplate.delete(key);
    }

    /**
     * 记录最后失败时间
     */
    private void recordLastFailureTime(String serviceName) {
        String key = LAST_FAILURE_TIME_KEY + serviceName;
        redisTemplate.opsForValue().set(key, System.currentTimeMillis(), TIMEOUT_DURATION, TimeUnit.MILLISECONDS);
    }

    /**
     * 检查是否可以尝试重置熔断器
     */
    private boolean canAttemptReset(String serviceName) {
        String key = LAST_FAILURE_TIME_KEY + serviceName;
        Long lastFailureTime = (Long) redisTemplate.opsForValue().get(key);
        
        if (lastFailureTime == null) {
            return true;
        }
        
        return System.currentTimeMillis() - lastFailureTime > RECOVERY_TIMEOUT;
    }

    /**
     * 特别关心服务降级方法
     */
    public static class AttentionFallback {
        
        /**
         * 获取特别关心列表降级方法
         */
        public static Object getAttentionListFallback() {
            log.warn("获取特别关心列表服务降级");
            return new Object(); // 返回空列表或缓存数据
        }

        /**
         * 添加特别关心降级方法
         */
        public static Object addAttentionFallback() {
            log.warn("添加特别关心服务降级");
            throw new RuntimeException("服务暂时不可用，请稍后重试");
        }

        /**
         * 删除特别关心降级方法
         */
        public static Object removeAttentionFallback() {
            log.warn("删除特别关心服务降级");
            throw new RuntimeException("服务暂时不可用，请稍后重试");
        }

        /**
         * 用户状态更新降级方法
         */
        public static Object updateUserStatusFallback() {
            log.warn("用户状态更新服务降级");
            return null; // 静默失败，不影响用户体验
        }

        /**
         * 通知发送降级方法
         */
        public static Object sendNotificationFallback() {
            log.warn("通知发送服务降级");
            return null; // 静默失败，可以稍后重试
        }
    }

    /**
     * 获取熔断器统计信息
     */
    public Object getCircuitBreakerStats() {
        // 可以返回各个服务的熔断器状态统计
        return new Object();
    }

    /**
     * 手动重置熔断器
     */
    public void resetCircuitBreaker(String serviceName) {
        setCircuitState(serviceName, CircuitState.CLOSED);
        resetFailureCount(serviceName);
        
        String lastFailureKey = LAST_FAILURE_TIME_KEY + serviceName;
        redisTemplate.delete(lastFailureKey);
        
        log.info("手动重置熔断器 - 服务: {}", serviceName);
    }

    /**
     * 检查服务健康状态
     */
    public boolean isServiceHealthy(String serviceName) {
        CircuitState state = getCircuitState(serviceName);
        return state == CircuitState.CLOSED;
    }
}
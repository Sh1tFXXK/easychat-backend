package org.example.easychat.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.BO.ApiResponseBO;
import org.example.easychat.service.CircuitBreakerService;
import org.example.easychat.service.ErrorMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 错误监控控制器
 * 提供错误监控和熔断器管理的API接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/error-monitor")
public class ErrorMonitorController {

    @Autowired
    private ErrorMonitorService errorMonitorService;

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    /**
     * 获取错误统计信息
     */
    @GetMapping("/statistics")
    public ApiResponseBO<Map<String, Object>> getErrorStatistics(
            @RequestParam(defaultValue = "hour") String timeRange) {
        try {
            Map<String, Object> statistics = errorMonitorService.getErrorStatistics(timeRange);
            return new ApiResponseBO<>(true, "获取错误统计成功", 200, statistics);
        } catch (Exception e) {
            log.error("获取错误统计失败", e);
            return new ApiResponseBO<>(false, "获取错误统计失败: " + e.getMessage(), 500, null);
        }
    }

    /**
     * 获取系统健康状态
     */
    @GetMapping("/health")
    public ApiResponseBO<Map<String, Object>> getSystemHealth() {
        try {
            Map<String, Object> health = new HashMap<>();
            
            // 检查各个服务的健康状态
            health.put("attentionService", circuitBreakerService.isServiceHealthy("SpecialAttentionService"));
            health.put("userStatusService", circuitBreakerService.isServiceHealthy("UserStatusService"));
            health.put("notificationService", circuitBreakerService.isServiceHealthy("NotificationService"));
            health.put("databaseService", circuitBreakerService.isServiceHealthy("DatabaseService"));
            health.put("cacheService", circuitBreakerService.isServiceHealthy("CacheService"));
            
            // 计算整体健康状态
            boolean overallHealth = health.values().stream()
                .allMatch(status -> Boolean.TRUE.equals(status));
            health.put("overall", overallHealth);
            
            return new ApiResponseBO<>(true, "获取系统健康状态成功", 200, health);
        } catch (Exception e) {
            log.error("获取系统健康状态失败", e);
            return new ApiResponseBO<>(false, "获取系统健康状态失败: " + e.getMessage(), 500, null);
        }
    }

    /**
     * 获取熔断器状态
     */
    @GetMapping("/circuit-breaker")
    public ApiResponseBO<Object> getCircuitBreakerStats() {
        try {
            Object stats = circuitBreakerService.getCircuitBreakerStats();
            return new ApiResponseBO<>(true, "获取熔断器状态成功", 200, stats);
        } catch (Exception e) {
            log.error("获取熔断器状态失败", e);
            return new ApiResponseBO<>(false, "获取熔断器状态失败: " + e.getMessage(), 500, null);
        }
    }

    /**
     * 重置熔断器
     */
    @PostMapping("/circuit-breaker/reset")
    public ApiResponseBO<Void> resetCircuitBreaker(@RequestParam String serviceName) {
        try {
            circuitBreakerService.resetCircuitBreaker(serviceName);
            log.info("手动重置熔断器成功 - 服务: {}", serviceName);
            return new ApiResponseBO<>(true, "重置熔断器成功", 200, null);
        } catch (Exception e) {
            log.error("重置熔断器失败 - 服务: {}", serviceName, e);
            return new ApiResponseBO<>(false, "重置熔断器失败: " + e.getMessage(), 500, null);
        }
    }

    /**
     * 手动清理过期数据
     */
    @PostMapping("/cleanup")
    public ApiResponseBO<Void> cleanupExpiredData() {
        try {
            errorMonitorService.cleanupExpiredData();
            log.info("手动清理过期数据成功");
            return new ApiResponseBO<>(true, "清理过期数据成功", 200, null);
        } catch (Exception e) {
            log.error("清理过期数据失败", e);
            return new ApiResponseBO<>(false, "清理过期数据失败: " + e.getMessage(), 500, null);
        }
    }

    /**
     * 测试错误处理
     */
    @PostMapping("/test-error")
    public ApiResponseBO<Void> testError(@RequestParam String errorType) {
        try {
            switch (errorType) {
                case "business":
                    throw new RuntimeException("测试业务异常");
                case "database":
                    throw new org.springframework.dao.DataAccessException("测试数据库异常") {};
                case "validation":
                    throw new IllegalArgumentException("测试参数验证异常");
                default:
                    throw new RuntimeException("未知错误类型");
            }
        } catch (Exception e) {
            log.error("测试错误处理 - 错误类型: {}", errorType, e);
            return new ApiResponseBO<>(false, "测试错误: " + e.getMessage(), 500, null);
        }
    }

    /**
     * 获取错误处理配置
     */
    @GetMapping("/config")
    public ApiResponseBO<Map<String, Object>> getErrorHandlingConfig() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("errorThresholds", Map.of(
                "BUSINESS_ERROR", 100,
                "DATABASE_ERROR", 50,
                "VALIDATION_ERROR", 200
            ));
            config.put("errorRateThresholds", Map.of(
                "BUSINESS_ERROR", 10.0,
                "DATABASE_ERROR", 5.0
            ));
            config.put("circuitBreakerConfig", Map.of(
                "failureThreshold", 5,
                "timeoutDuration", 60000,
                "recoveryTimeout", 30000
            ));
            
            return new ApiResponseBO<>(true, "获取错误处理配置成功", 200, config);
        } catch (Exception e) {
            log.error("获取错误处理配置失败", e);
            return new ApiResponseBO<>(false, "获取错误处理配置失败: " + e.getMessage(), 500, null);
        }
    }
}
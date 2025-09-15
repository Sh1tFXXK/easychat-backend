package org.example.easychat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 错误监控服务
 * 用于记录和监控特别关心功能的错误情况
 */
@Slf4j
@Service
public class ErrorMonitorService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String ERROR_METRICS_KEY = "error:metrics:";
    private static final String ERROR_COUNT_KEY = "error:count:";
    private static final String ERROR_RATE_KEY = "error:rate:";
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 记录错误指标
     */
    public void recordError(String errorType, int errorCode, String requestUri) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String hourKey = now.format(HOUR_FORMATTER);
            String dayKey = now.format(DAY_FORMATTER);

            // 记录小时级别的错误统计
            recordHourlyError(errorType, errorCode, requestUri, hourKey);
            
            // 记录日级别的错误统计
            recordDailyError(errorType, errorCode, requestUri, dayKey);
            
            // 更新错误率统计
            updateErrorRate(errorType, hourKey);
            
            // 检查是否需要告警
            checkErrorThreshold(errorType, hourKey);
            
        } catch (Exception e) {
            log.error("记录错误指标失败", e);
        }
    }

    /**
     * 记录小时级别错误
     */
    private void recordHourlyError(String errorType, int errorCode, String requestUri, String hourKey) {
        String key = ERROR_METRICS_KEY + "hourly:" + hourKey;
        
        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("errorType", errorType);
        errorInfo.put("errorCode", errorCode);
        errorInfo.put("requestUri", requestUri);
        errorInfo.put("timestamp", LocalDateTime.now().toString());
        
        // 使用Hash存储错误详情
        redisTemplate.opsForHash().increment(key, errorType, 1);
        redisTemplate.opsForHash().increment(key, "total", 1);
        
        // 设置过期时间为7天
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
        
        // 记录错误详情（最近100条）
        String detailKey = ERROR_METRICS_KEY + "detail:" + errorType + ":" + hourKey;
        redisTemplate.opsForList().leftPush(detailKey, errorInfo);
        redisTemplate.opsForList().trim(detailKey, 0, 99); // 只保留最近100条
        redisTemplate.expire(detailKey, 1, TimeUnit.DAYS);
    }

    /**
     * 记录日级别错误
     */
    private void recordDailyError(String errorType, int errorCode, String requestUri, String dayKey) {
        String key = ERROR_METRICS_KEY + "daily:" + dayKey;
        
        redisTemplate.opsForHash().increment(key, errorType, 1);
        redisTemplate.opsForHash().increment(key, "total", 1);
        
        // 设置过期时间为30天
        redisTemplate.expire(key, 30, TimeUnit.DAYS);
    }

    /**
     * 更新错误率统计
     */
    private void updateErrorRate(String errorType, String hourKey) {
        String errorCountKey = ERROR_COUNT_KEY + errorType + ":" + hourKey;
        String totalCountKey = ERROR_COUNT_KEY + "total:" + hourKey;
        
        // 增加错误计数
        redisTemplate.opsForValue().increment(errorCountKey);
        redisTemplate.opsForValue().increment(totalCountKey);
        
        // 设置过期时间
        redisTemplate.expire(errorCountKey, 1, TimeUnit.DAYS);
        redisTemplate.expire(totalCountKey, 1, TimeUnit.DAYS);
        
        // 计算错误率
        Long errorCount = (Long) redisTemplate.opsForValue().get(errorCountKey);
        Long totalCount = (Long) redisTemplate.opsForValue().get(totalCountKey);
        
        if (errorCount != null && totalCount != null && totalCount > 0) {
            double errorRate = (double) errorCount / totalCount * 100;
            String rateKey = ERROR_RATE_KEY + errorType + ":" + hourKey;
            redisTemplate.opsForValue().set(rateKey, errorRate, 1, TimeUnit.DAYS);
        }
    }

    /**
     * 检查错误阈值并告警
     */
    private void checkErrorThreshold(String errorType, String hourKey) {
        String errorCountKey = ERROR_COUNT_KEY + errorType + ":" + hourKey;
        Long errorCount = (Long) redisTemplate.opsForValue().get(errorCountKey);
        
        if (errorCount != null) {
            // 检查错误数量阈值
            if (errorCount > getErrorThreshold(errorType)) {
                triggerErrorAlert(errorType, errorCount, hourKey);
            }
            
            // 检查错误率阈值
            String rateKey = ERROR_RATE_KEY + errorType + ":" + hourKey;
            Double errorRate = (Double) redisTemplate.opsForValue().get(rateKey);
            if (errorRate != null && errorRate > getErrorRateThreshold(errorType)) {
                triggerErrorRateAlert(errorType, errorRate, hourKey);
            }
        }
    }

    /**
     * 获取错误数量阈值
     */
    private long getErrorThreshold(String errorType) {
        switch (errorType) {
            case "BUSINESS_ERROR":
                return 100; // 业务错误每小时超过100次告警
            case "DATABASE_ERROR":
                return 50;  // 数据库错误每小时超过50次告警
            case "VALIDATION_ERROR":
                return 200; // 参数验证错误每小时超过200次告警
            default:
                return 50;  // 默认阈值
        }
    }

    /**
     * 获取错误率阈值
     */
    private double getErrorRateThreshold(String errorType) {
        switch (errorType) {
            case "BUSINESS_ERROR":
                return 10.0; // 业务错误率超过10%告警
            case "DATABASE_ERROR":
                return 5.0;  // 数据库错误率超过5%告警
            default:
                return 5.0;  // 默认错误率阈值
        }
    }

    /**
     * 触发错误数量告警
     */
    private void triggerErrorAlert(String errorType, Long errorCount, String hourKey) {
        log.error("错误数量告警 - 错误类型: {}, 错误数量: {}, 时间: {}", errorType, errorCount, hourKey);
        
        // 这里可以集成告警系统，如发送邮件、短信、钉钉等
        // 为了避免重复告警，可以设置告警间隔
        String alertKey = "alert:count:" + errorType + ":" + hourKey;
        Boolean alerted = redisTemplate.hasKey(alertKey);
        
        if (!alerted) {
            // 发送告警
            sendAlert("错误数量告警", 
                String.format("错误类型: %s, 错误数量: %d, 时间: %s", errorType, errorCount, hourKey));
            
            // 设置告警标记，1小时内不重复告警
            redisTemplate.opsForValue().set(alertKey, true, 1, TimeUnit.HOURS);
        }
    }

    /**
     * 触发错误率告警
     */
    private void triggerErrorRateAlert(String errorType, Double errorRate, String hourKey) {
        log.error("错误率告警 - 错误类型: {}, 错误率: {}%, 时间: {}", errorType, errorRate, hourKey);
        
        String alertKey = "alert:rate:" + errorType + ":" + hourKey;
        Boolean alerted = redisTemplate.hasKey(alertKey);
        
        if (!alerted) {
            sendAlert("错误率告警", 
                String.format("错误类型: %s, 错误率: %.2f%%, 时间: %s", errorType, errorRate, hourKey));
            
            redisTemplate.opsForValue().set(alertKey, true, 1, TimeUnit.HOURS);
        }
    }

    /**
     * 发送告警
     */
    private void sendAlert(String title, String message) {
        // 这里可以实现具体的告警发送逻辑
        // 例如：发送邮件、短信、钉钉消息等
        log.warn("告警通知 - {}: {}", title, message);
        
        // 可以异步发送告警，避免影响主流程
        // CompletableFuture.runAsync(() -> {
        //     // 发送告警的具体实现
        // });
    }

    /**
     * 获取错误统计信息
     */
    public Map<String, Object> getErrorStatistics(String timeRange) {
        Map<String, Object> statistics = new HashMap<>();
        
        try {
            LocalDateTime now = LocalDateTime.now();
            String key;
            
            if ("hour".equals(timeRange)) {
                key = ERROR_METRICS_KEY + "hourly:" + now.format(HOUR_FORMATTER);
            } else {
                key = ERROR_METRICS_KEY + "daily:" + now.format(DAY_FORMATTER);
            }
            
            Map<Object, Object> errorCounts = redisTemplate.opsForHash().entries(key);
            statistics.put("errorCounts", errorCounts);
            
            // 获取错误率信息
            Map<String, Double> errorRates = new HashMap<>();
            for (Object errorType : errorCounts.keySet()) {
                if (!"total".equals(errorType)) {
                    String rateKey = ERROR_RATE_KEY + errorType + ":" + now.format(HOUR_FORMATTER);
                    Double rate = (Double) redisTemplate.opsForValue().get(rateKey);
                    if (rate != null) {
                        errorRates.put(errorType.toString(), rate);
                    }
                }
            }
            statistics.put("errorRates", errorRates);
            
        } catch (Exception e) {
            log.error("获取错误统计信息失败", e);
        }
        
        return statistics;
    }

    /**
     * 清理过期的错误统计数据
     */
    public void cleanupExpiredData() {
        try {
            // 这个方法可以通过定时任务调用，清理过期的统计数据
            log.info("开始清理过期的错误统计数据");
            
            // 具体的清理逻辑可以根据需要实现
            // 例如：删除超过30天的日统计数据，删除超过7天的小时统计数据
            
        } catch (Exception e) {
            log.error("清理过期错误统计数据失败", e);
        }
    }
}
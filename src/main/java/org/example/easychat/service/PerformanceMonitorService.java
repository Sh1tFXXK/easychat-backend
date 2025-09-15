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
 * 性能监控服务
 */
@Slf4j
@Service
public class PerformanceMonitorService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String METRICS_PREFIX = "metrics:";
    private static final String RESPONSE_TIME_PREFIX = "response_time:";
    private static final String ERROR_COUNT_PREFIX = "error_count:";
    private static final String REQUEST_COUNT_PREFIX = "request_count:";
    
    /**
     * 记录API响应时间
     */
    public void recordResponseTime(String apiName, long responseTimeMs) {
        try {
            String key = METRICS_PREFIX + RESPONSE_TIME_PREFIX + apiName;
            String timeKey = getCurrentTimeKey();
            
            // 使用Hash存储每分钟的响应时间统计
            redisTemplate.opsForHash().increment(key, timeKey + ":count", 1);
            redisTemplate.opsForHash().increment(key, timeKey + ":total", responseTimeMs);
            
            // 设置过期时间为24小时
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
            
            // 记录慢查询（超过200ms）
            if (responseTimeMs > 200) {
                recordSlowQuery(apiName, responseTimeMs);
            }
            
            log.debug("记录API响应时间: api={}, responseTime={}ms", apiName, responseTimeMs);
            
        } catch (Exception e) {
            log.error("记录API响应时间失败: api={}, responseTime={}", apiName, responseTimeMs, e);
        }
    }
    
    /**
     * 记录API请求次数
     */
    public void recordRequestCount(String apiName) {
        try {
            String key = METRICS_PREFIX + REQUEST_COUNT_PREFIX + apiName;
            String timeKey = getCurrentTimeKey();
            
            redisTemplate.opsForHash().increment(key, timeKey, 1);
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
            
            log.debug("记录API请求次数: api={}", apiName);
            
        } catch (Exception e) {
            log.error("记录API请求次数失败: api={}", apiName, e);
        }
    }
    
    /**
     * 记录错误次数
     */
    public void recordErrorCount(String apiName, String errorType) {
        try {
            String key = METRICS_PREFIX + ERROR_COUNT_PREFIX + apiName;
            String timeKey = getCurrentTimeKey() + ":" + errorType;
            
            redisTemplate.opsForHash().increment(key, timeKey, 1);
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
            
            log.debug("记录API错误次数: api={}, errorType={}", apiName, errorType);
            
        } catch (Exception e) {
            log.error("记录API错误次数失败: api={}, errorType={}", apiName, errorType, e);
        }
    }
    
    /**
     * 记录慢查询
     */
    private void recordSlowQuery(String apiName, long responseTimeMs) {
        try {
            String key = METRICS_PREFIX + "slow_queries:" + apiName;
            String value = getCurrentTimeKey() + ":" + responseTimeMs;
            
            // 使用List存储慢查询记录，最多保留100条
            redisTemplate.opsForList().leftPush(key, value);
            redisTemplate.opsForList().trim(key, 0, 99);
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
            
            log.warn("记录慢查询: api={}, responseTime={}ms", apiName, responseTimeMs);
            
        } catch (Exception e) {
            log.error("记录慢查询失败: api={}, responseTime={}", apiName, responseTimeMs, e);
        }
    }
    
    /**
     * 获取API性能统计
     */
    public Map<String, Object> getApiPerformanceStats(String apiName) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 获取响应时间统计
            String responseTimeKey = METRICS_PREFIX + RESPONSE_TIME_PREFIX + apiName;
            Map<Object, Object> responseTimeData = redisTemplate.opsForHash().entries(responseTimeKey);
            
            long totalRequests = 0;
            long totalResponseTime = 0;
            
            for (Map.Entry<Object, Object> entry : responseTimeData.entrySet()) {
                String key = entry.getKey().toString();
                long value = Long.parseLong(entry.getValue().toString());
                
                if (key.endsWith(":count")) {
                    totalRequests += value;
                } else if (key.endsWith(":total")) {
                    totalResponseTime += value;
                }
            }
            
            // 计算平均响应时间
            double avgResponseTime = totalRequests > 0 ? (double) totalResponseTime / totalRequests : 0;
            
            stats.put("totalRequests", totalRequests);
            stats.put("avgResponseTime", avgResponseTime);
            
            // 获取错误统计
            String errorCountKey = METRICS_PREFIX + ERROR_COUNT_PREFIX + apiName;
            Map<Object, Object> errorData = redisTemplate.opsForHash().entries(errorCountKey);
            
            long totalErrors = 0;
            for (Object value : errorData.values()) {
                totalErrors += Long.parseLong(value.toString());
            }
            
            stats.put("totalErrors", totalErrors);
            stats.put("errorRate", totalRequests > 0 ? (double) totalErrors / totalRequests : 0);
            
            // 获取慢查询统计
            String slowQueryKey = METRICS_PREFIX + "slow_queries:" + apiName;
            Long slowQueryCount = redisTemplate.opsForList().size(slowQueryKey);
            stats.put("slowQueryCount", slowQueryCount != null ? slowQueryCount : 0);
            
            log.debug("获取API性能统计: api={}, stats={}", apiName, stats);
            
        } catch (Exception e) {
            log.error("获取API性能统计失败: api={}", apiName, e);
        }
        
        return stats;
    }
    
    /**
     * 获取系统整体性能统计
     */
    public Map<String, Object> getSystemPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 获取所有API的统计信息
            String[] apis = {
                "getAttentionList",
                "addAttention", 
                "removeAttention",
                "updateAttention",
                "batchOperation",
                "checkAttentionStatus"
            };
            
            long totalRequests = 0;
            long totalErrors = 0;
            double totalResponseTime = 0;
            
            for (String api : apis) {
                Map<String, Object> apiStats = getApiPerformanceStats(api);
                
                long requests = (Long) apiStats.getOrDefault("totalRequests", 0L);
                long errors = (Long) apiStats.getOrDefault("totalErrors", 0L);
                double avgResponseTime = (Double) apiStats.getOrDefault("avgResponseTime", 0.0);
                
                totalRequests += requests;
                totalErrors += errors;
                totalResponseTime += avgResponseTime * requests;
            }
            
            stats.put("totalRequests", totalRequests);
            stats.put("totalErrors", totalErrors);
            stats.put("avgResponseTime", totalRequests > 0 ? totalResponseTime / totalRequests : 0);
            stats.put("errorRate", totalRequests > 0 ? (double) totalErrors / totalRequests : 0);
            
            // 获取缓存命中率
            stats.put("cacheHitRate", getCacheHitRate());
            
            // 获取在线用户数
            Long onlineUserCount = redisTemplate.opsForSet().size("online:users");
            stats.put("onlineUserCount", onlineUserCount != null ? onlineUserCount : 0);
            
            log.debug("获取系统性能统计: stats={}", stats);
            
        } catch (Exception e) {
            log.error("获取系统性能统计失败", e);
        }
        
        return stats;
    }
    
    /**
     * 记录缓存命中情况
     */
    public void recordCacheHit(String cacheType, boolean hit) {
        try {
            String key = METRICS_PREFIX + "cache:" + cacheType;
            String timeKey = getCurrentTimeKey();
            
            redisTemplate.opsForHash().increment(key, timeKey + ":total", 1);
            if (hit) {
                redisTemplate.opsForHash().increment(key, timeKey + ":hit", 1);
            }
            
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
            
            log.debug("记录缓存命中: cacheType={}, hit={}", cacheType, hit);
            
        } catch (Exception e) {
            log.error("记录缓存命中失败: cacheType={}, hit={}", cacheType, hit, e);
        }
    }
    
    /**
     * 获取缓存命中率
     */
    private double getCacheHitRate() {
        try {
            String[] cacheTypes = {"user_status", "attention_list", "attention_count"};
            
            long totalRequests = 0;
            long totalHits = 0;
            
            for (String cacheType : cacheTypes) {
                String key = METRICS_PREFIX + "cache:" + cacheType;
                Map<Object, Object> cacheData = redisTemplate.opsForHash().entries(key);
                
                for (Map.Entry<Object, Object> entry : cacheData.entrySet()) {
                    String entryKey = entry.getKey().toString();
                    long value = Long.parseLong(entry.getValue().toString());
                    
                    if (entryKey.endsWith(":total")) {
                        totalRequests += value;
                    } else if (entryKey.endsWith(":hit")) {
                        totalHits += value;
                    }
                }
            }
            
            return totalRequests > 0 ? (double) totalHits / totalRequests : 0;
            
        } catch (Exception e) {
            log.error("获取缓存命中率失败", e);
            return 0;
        }
    }
    
    /**
     * 获取当前时间键（精确到分钟）
     */
    private String getCurrentTimeKey() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm"));
    }
    
    /**
     * 清理过期的性能数据
     */
    public void cleanExpiredMetrics() {
        try {
            // 这里可以实现清理逻辑，由于设置了过期时间，Redis会自动清理
            log.info("性能数据清理完成");
        } catch (Exception e) {
            log.error("清理性能数据失败", e);
        }
    }
}
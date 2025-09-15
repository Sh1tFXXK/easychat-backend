package org.example.easychat.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

/**
 * 限流配置
 */
@Slf4j
@Configuration
public class RateLimitConfig {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 限流Lua脚本
     * 使用滑动窗口算法实现限流
     */
    @Bean
    public RedisScript<Long> rateLimitScript() {
        String script = 
            "local key = KEYS[1]\n" +
            "local window = tonumber(ARGV[1])\n" +
            "local limit = tonumber(ARGV[2])\n" +
            "local current = tonumber(ARGV[3])\n" +
            "\n" +
            "-- 清理过期的记录\n" +
            "redis.call('zremrangebyscore', key, 0, current - window * 1000)\n" +
            "\n" +
            "-- 获取当前窗口内的请求数量\n" +
            "local currentCount = redis.call('zcard', key)\n" +
            "\n" +
            "if currentCount < limit then\n" +
            "    -- 添加当前请求\n" +
            "    redis.call('zadd', key, current, current)\n" +
            "    -- 设置过期时间\n" +
            "    redis.call('expire', key, window + 1)\n" +
            "    return limit - currentCount - 1\n" +
            "else\n" +
            "    return -1\n" +
            "end";
        
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        return redisScript;
    }
    
    /**
     * 检查是否被限流
     * @param key 限流键
     * @param windowSeconds 时间窗口（秒）
     * @param limit 限制次数
     * @return 剩余次数，-1表示被限流
     */
    public long checkRateLimit(String key, int windowSeconds, int limit) {
        try {
            List<String> keys = Collections.singletonList(key);
            long currentTime = System.currentTimeMillis();
            
            Long result = redisTemplate.execute(
                rateLimitScript(),
                keys,
                String.valueOf(windowSeconds),
                String.valueOf(limit),
                String.valueOf(currentTime)
            );
            
            if (result != null && result >= 0) {
                log.debug("限流检查通过: key={}, remaining={}", key, result);
                return result;
            } else {
                log.warn("触发限流: key={}, window={}s, limit={}", key, windowSeconds, limit);
                return -1;
            }
            
        } catch (Exception e) {
            log.error("限流检查失败: key={}", key, e);
            // 发生异常时允许通过，避免影响正常业务
            return limit - 1;
        }
    }
    
    /**
     * 获取限流键
     */
    public String getRateLimitKey(String prefix, String identifier) {
        return "rate_limit:" + prefix + ":" + identifier;
    }
    
    // 预定义的限流配置
    public static class RateLimitConstants {
        // API接口限流
        public static final String API_ADD_ATTENTION = "api:add_attention";
        public static final String API_REMOVE_ATTENTION = "api:remove_attention";
        public static final String API_UPDATE_ATTENTION = "api:update_attention";
        public static final String API_BATCH_OPERATION = "api:batch_operation";
        public static final String API_QUERY_ATTENTION = "api:query_attention";
        
        // Socket.IO事件限流
        public static final String SOCKET_ATTENTION_UPDATE = "socket:attention_update";
        public static final String SOCKET_STATUS_CHANGE = "socket:status_change";
        
        // 限流参数
        public static final int ADD_ATTENTION_WINDOW = 60; // 1分钟
        public static final int ADD_ATTENTION_LIMIT = 10;  // 最多10次
        
        public static final int REMOVE_ATTENTION_WINDOW = 60; // 1分钟
        public static final int REMOVE_ATTENTION_LIMIT = 20;  // 最多20次
        
        public static final int UPDATE_ATTENTION_WINDOW = 60; // 1分钟
        public static final int UPDATE_ATTENTION_LIMIT = 30;  // 最多30次
        
        public static final int BATCH_OPERATION_WINDOW = 300; // 5分钟
        public static final int BATCH_OPERATION_LIMIT = 5;    // 最多5次
        
        public static final int QUERY_ATTENTION_WINDOW = 60;  // 1分钟
        public static final int QUERY_ATTENTION_LIMIT = 100;  // 最多100次
        
        public static final int SOCKET_EVENT_WINDOW = 60;     // 1分钟
        public static final int SOCKET_EVENT_LIMIT = 50;      // 最多50次
    }
}
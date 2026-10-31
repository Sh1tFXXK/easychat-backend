package org.example.easychat.dto;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 优化状态DTO
 */
@Data
@Builder
public class OptimizationStatusDto {
    
    /**
     * WebSocket连接统计
     */
    private WebSocketStats webSocketStats;
    
    /**
     * 缓存统计
     */
    private CacheStats cacheStats;
    
    /**
     * 消息队列统计
     */
    private MessageQueueStats messageQueueStats;
    
    /**
     * 数据库性能统计
     */
    private DatabaseStats databaseStats;
    
    /**
     * 系统资源使用情况
     */
    private SystemStats systemStats;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdated;
    
    @Data
    @Builder
    public static class WebSocketStats {
        private Integer activeConnections;
        private Integer maxConnections;
        private Long totalMessages;
        private Double averageResponseTime;
        private Integer connectionsPerSecond;
    }
    
    @Data
    @Builder
    public static class CacheStats {
        private Double hitRate;
        private Long totalHits;
        private Long totalMisses;
        private Integer cachedSessions;
        private Integer cachedChatLists;
        private Long memoryUsage;
    }
    
    @Data
    @Builder
    public static class MessageQueueStats {
        private Integer queueSize;
        private Long processedMessages;
        private Long failedMessages;
        private Double throughput;
        private Integer retryCount;
    }
    
    @Data
    @Builder
    public static class DatabaseStats {
        private Double averageQueryTime;
        private Integer slowQueries;
        private Long totalQueries;
        private Double connectionPoolUsage;
        private Map<String, Object> indexUsage;
    }
    
    @Data
    @Builder
    public static class SystemStats {
        private Double cpuUsage;
        private Double memoryUsage;
        private Double diskUsage;
        private Integer threadCount;
        private Long uptime;
    }
}
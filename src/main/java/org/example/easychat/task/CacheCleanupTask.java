package org.example.easychat.task;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.service.CacheService;
import org.example.easychat.service.PerformanceMonitorService;
import org.example.easychat.service.UserStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 缓存清理和性能监控定时任务
 */
@Slf4j
@Component
public class CacheCleanupTask {
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private PerformanceMonitorService performanceMonitorService;
    
    @Autowired
    private UserStatusService userStatusService;
    
    /**
     * 清理过期的性能监控数据
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanExpiredMetrics() {
        try {
            log.info("开始清理过期的性能监控数据");
            performanceMonitorService.cleanExpiredMetrics();
            log.info("清理过期的性能监控数据完成");
        } catch (Exception e) {
            log.error("清理过期的性能监控数据失败", e);
        }
    }
    
    /**
     * 清理长时间未活跃的用户状态
     * 每6小时执行一次
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    public void cleanInactiveUserStatus() {
        try {
            log.info("开始清理长时间未活跃的用户状态");
            // 清理24小时未活跃的用户状态
            userStatusService.cleanInactiveUsers(24);
            log.info("清理长时间未活跃的用户状态完成");
        } catch (Exception e) {
            log.error("清理长时间未活跃的用户状态失败", e);
        }
    }
    
    /**
     * 生成性能报告
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void generatePerformanceReport() {
        try {
            log.info("开始生成性能报告");
            
            // 获取系统性能统计
            var stats = performanceMonitorService.getSystemPerformanceStats();
            
            log.info("系统性能报告: {}", stats);
            
            // 这里可以将报告发送到监控系统或保存到文件
            
            log.info("生成性能报告完成");
        } catch (Exception e) {
            log.error("生成性能报告失败", e);
        }
    }
    
    /**
     * 缓存预热
     * 每天凌晨3点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void warmUpCache() {
        try {
            log.info("开始缓存预热");
            
            // 获取在线用户列表进行缓存预热
            var onlineUsers = userStatusService.getOnlineUsers();
            if (!onlineUsers.isEmpty()) {
                cacheService.warmUpCache(onlineUsers.stream().toList());
            }
            
            log.info("缓存预热完成: 预热用户数={}", onlineUsers.size());
        } catch (Exception e) {
            log.error("缓存预热失败", e);
        }
    }
    
    /**
     * 监控系统健康状态
     * 每5分钟执行一次
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void monitorSystemHealth() {
        try {
            // 检查在线用户数
            long onlineUserCount = userStatusService.getOnlineUserCount();
            
            // 检查缓存命中率等关键指标
            var systemStats = performanceMonitorService.getSystemPerformanceStats();
            
            // 记录关键指标
            log.debug("系统健康检查: 在线用户数={}, 系统统计={}", onlineUserCount, systemStats);
            
            // 如果发现异常指标，可以触发告警
            double errorRate = (Double) systemStats.getOrDefault("errorRate", 0.0);
            if (errorRate > 0.1) { // 错误率超过10%
                log.warn("系统错误率过高: errorRate={}", errorRate);
            }
            
            double avgResponseTime = (Double) systemStats.getOrDefault("avgResponseTime", 0.0);
            if (avgResponseTime > 1000) { // 平均响应时间超过1秒
                log.warn("系统响应时间过长: avgResponseTime={}ms", avgResponseTime);
            }
            
        } catch (Exception e) {
            log.error("系统健康检查失败", e);
        }
    }
}
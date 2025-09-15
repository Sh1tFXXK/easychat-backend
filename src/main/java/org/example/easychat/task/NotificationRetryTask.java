package org.example.easychat.task;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知重试定时任务
 */
@Slf4j
@Component
public class NotificationRetryTask {
    
    @Autowired
    private NotificationService notificationService;
    
    /**
     * 每分钟执行一次重试任务
     */
    @Scheduled(fixedRate = 60000) // 60秒
    public void retryFailedNotifications() {
        try {
            log.debug("开始执行通知重试任务");
            notificationService.retryFailedNotifications();
            log.debug("通知重试任务执行完成");
        } catch (Exception e) {
            log.error("执行通知重试任务失败", e);
        }
    }
    
    /**
     * 每小时清理过期的重试记录
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void cleanExpiredRetryRecords() {
        try {
            log.debug("开始清理过期的重试记录");
            // 这里可以添加清理逻辑
            log.debug("清理过期重试记录完成");
        } catch (Exception e) {
            log.error("清理过期重试记录失败", e);
        }
    }
}
package org.example.easychat.task;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.service.ErrorMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 错误数据清理定时任务
 * 定期清理过期的错误监控数据
 */
@Slf4j
@Component
public class ErrorCleanupTask {

    @Autowired
    private ErrorMonitorService errorMonitorService;

    /**
     * 每天凌晨2点清理过期的错误统计数据
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredErrorData() {
        try {
            log.info("开始执行错误数据清理任务");
            errorMonitorService.cleanupExpiredData();
            log.info("错误数据清理任务执行完成");
        } catch (Exception e) {
            log.error("错误数据清理任务执行失败", e);
        }
    }

    /**
     * 每小时生成错误统计报告
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void generateErrorReport() {
        try {
            log.info("开始生成错误统计报告");
            
            // 获取小时级别的错误统计
            var hourlyStats = errorMonitorService.getErrorStatistics("hour");
            log.info("小时错误统计: {}", hourlyStats);
            
            // 这里可以将统计数据发送到监控系统或生成报告
            
        } catch (Exception e) {
            log.error("生成错误统计报告失败", e);
        }
    }

    /**
     * 每天生成日错误统计报告
     */
    @Scheduled(cron = "0 30 1 * * ?")
    public void generateDailyErrorReport() {
        try {
            log.info("开始生成日错误统计报告");
            
            // 获取日级别的错误统计
            var dailyStats = errorMonitorService.getErrorStatistics("day");
            log.info("日错误统计: {}", dailyStats);
            
            // 这里可以将统计数据发送到监控系统或生成报告
            
        } catch (Exception e) {
            log.error("生成日错误统计报告失败", e);
        }
    }
}
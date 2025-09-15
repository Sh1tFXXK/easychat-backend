package org.example.easychat.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 通知服务配置
 */
@Configuration
@EnableRetry
@EnableScheduling
public class NotificationConfig {
    // 配置类，启用重试和定时任务功能
}
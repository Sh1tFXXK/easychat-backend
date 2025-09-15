package org.example.easychat.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 错误处理配置类
 * 启用AOP、异步处理和定时任务
 */
@Configuration
@EnableAspectJAutoProxy
@EnableAsync
@EnableScheduling
public class ErrorHandlingConfig {
    
    // AOP和异步处理已通过注解启用
    // 如果需要自定义配置，可以在这里添加Bean定义
}
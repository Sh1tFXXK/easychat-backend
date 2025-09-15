package org.example.easychat.listener;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.example.easychat.Config.RabbitMQConfig;
import org.example.easychat.dto.NotificationMessage;
import org.example.easychat.service.NotificationService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 通知消息监听器
 */
@Slf4j
@Component
public class NotificationMessageListener {
    
    @Autowired
    private NotificationService notificationService;
    
    /**
     * 处理普通优先级通知消息
     */
    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handleNotificationMessage(NotificationMessage message, Message amqpMessage, Channel channel) {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        
        try {
            log.info("接收到通知消息: type={}, userId={}", message.getType(), message.getUserId());
            
            // 处理通知消息
            notificationService.processNotificationMessage(message);
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            
            log.debug("通知消息处理完成: type={}, userId={}", message.getType(), message.getUserId());
            
        } catch (Exception e) {
            log.error("处理通知消息失败: {}", message, e);
            
            try {
                // 检查重试次数
                if (message.getRetryCount() < message.getMaxRetries()) {
                    // 拒绝消息并重新入队
                    channel.basicNack(deliveryTag, false, true);
                    log.warn("通知消息处理失败，重新入队: type={}, userId={}, retryCount={}", 
                            message.getType(), message.getUserId(), message.getRetryCount());
                } else {
                    // 超过重试次数，拒绝消息（进入死信队列）
                    channel.basicNack(deliveryTag, false, false);
                    log.error("通知消息重试次数超限，进入死信队列: type={}, userId={}", 
                            message.getType(), message.getUserId());
                }
            } catch (IOException ioException) {
                log.error("消息确认失败", ioException);
            }
        }
    }
    
    /**
     * 处理高优先级通知消息
     */
    @RabbitListener(queues = RabbitMQConfig.HIGH_PRIORITY_QUEUE)
    public void handleHighPriorityMessage(NotificationMessage message, Message amqpMessage, Channel channel) {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        
        try {
            log.info("接收到高优先级通知消息: type={}, userId={}", message.getType(), message.getUserId());
            
            // 处理高优先级通知消息
            notificationService.processNotificationMessage(message);
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            
            log.debug("高优先级通知消息处理完成: type={}, userId={}", message.getType(), message.getUserId());
            
        } catch (Exception e) {
            log.error("处理高优先级通知消息失败: {}", message, e);
            
            try {
                // 高优先级消息重试策略更激进
                if (message.getRetryCount() < message.getMaxRetries()) {
                    // 立即重试
                    channel.basicNack(deliveryTag, false, true);
                    log.warn("高优先级通知消息处理失败，立即重试: type={}, userId={}, retryCount={}", 
                            message.getType(), message.getUserId(), message.getRetryCount());
                } else {
                    // 超过重试次数，拒绝消息
                    channel.basicNack(deliveryTag, false, false);
                    log.error("高优先级通知消息重试次数超限，进入死信队列: type={}, userId={}", 
                            message.getType(), message.getUserId());
                }
            } catch (IOException ioException) {
                log.error("高优先级消息确认失败", ioException);
            }
        }
    }
    
    /**
     * 处理死信队列消息
     */
    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_DLQ)
    public void handleDeadLetterMessage(NotificationMessage message, Message amqpMessage, Channel channel) {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        
        try {
            log.warn("接收到死信队列消息: type={}, userId={}", message.getType(), message.getUserId());
            
            // 记录死信消息，用于后续分析和处理
            recordDeadLetterMessage(message);
            
            // 确认死信消息
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("处理死信队列消息失败: {}", message, e);
            
            try {
                // 死信消息处理失败，直接确认避免无限循环
                channel.basicAck(deliveryTag, false);
            } catch (IOException ioException) {
                log.error("死信消息确认失败", ioException);
            }
        }
    }
    
    /**
     * 记录死信消息
     */
    private void recordDeadLetterMessage(NotificationMessage message) {
        try {
            // 这里可以将死信消息记录到数据库或日志系统
            // 用于后续分析和手动处理
            log.error("死信消息详情: type={}, userId={}, targetUserId={}, content={}, retryCount={}", 
                    message.getType(), message.getUserId(), message.getTargetUserId(), 
                    message.getContent(), message.getRetryCount());
            
            // 可以发送告警通知给运维人员
            // alertService.sendDeadLetterAlert(message);
            
        } catch (Exception e) {
            log.error("记录死信消息失败", e);
        }
    }
}
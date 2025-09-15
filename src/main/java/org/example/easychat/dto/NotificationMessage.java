package org.example.easychat.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知消息DTO
 */
@Data
public class NotificationMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 通知类型
     */
    private String type;
    
    /**
     * 接收通知的用户ID
     */
    private String userId;
    
    /**
     * 触发通知的用户ID
     */
    private String targetUserId;
    
    /**
     * 通知内容
     */
    private String content;
    
    /**
     * 通知优先级
     */
    private NotificationPriority priority = NotificationPriority.NORMAL;
    
    /**
     * 通知数据
     */
    private Object data;
    
    /**
     * 创建时间
     */
    private LocalDateTime timestamp;
    
    /**
     * 重试次数
     */
    private int retryCount = 0;
    
    /**
     * 最大重试次数
     */
    private int maxRetries = 3;
    
    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;
    
    /**
     * 是否需要持久化
     */
    private boolean persistent = true;
    
    /**
     * 过期时间（秒）
     */
    private long expireSeconds = 86400; // 24小时
}
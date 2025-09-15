package org.example.easychat.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

// Add missing imports for event classes
import org.example.easychat.event.UserStatusChangeEvent;
import org.example.easychat.event.UserOnlineEvent;
import org.example.easychat.event.UserOfflineEvent;
import org.example.easychat.event.AttentionUpdateEvent;

/**
 * 特别关心事件发布器
 * 用于解耦服务间的循环依赖
 */
@Slf4j
@Component
public class AttentionEventPublisher {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    /**
     * 发布用户状态变化事件
     */
    public void publishUserStatusChangeEvent(String userId, String oldStatus, String newStatus) {
        try {
            UserStatusChangeEvent event = new UserStatusChangeEvent(this, userId, oldStatus, newStatus);
            eventPublisher.publishEvent(event);
            log.debug("发布用户状态变化事件: userId={}, oldStatus={}, newStatus={}", userId, oldStatus, newStatus);
        } catch (Exception e) {
            log.error("发布用户状态变化事件失败: userId={}, oldStatus={}, newStatus={}", userId, oldStatus, newStatus, e);
        }
    }
    
    /**
     * 发布特别关心更新事件
     */
    public void publishAttentionUpdateEvent(String userId, String targetUserId, String operation) {
        try {
            AttentionUpdateEvent event = new AttentionUpdateEvent(this, userId, targetUserId, operation);
            eventPublisher.publishEvent(event);
            log.debug("发布特别关心更新事件: userId={}, targetUserId={}, operation={}", userId, targetUserId, operation);
        } catch (Exception e) {
            log.error("发布特别关心更新事件失败: userId={}, targetUserId={}, operation={}", userId, targetUserId, operation, e);
        }
    }
    
    /**
     * 发布用户上线事件
     */
    public void publishUserOnlineEvent(String userId) {
        try {
            UserOnlineEvent event = new UserOnlineEvent(this, userId);
            eventPublisher.publishEvent(event);
            log.debug("发布用户上线事件: userId={}", userId);
        } catch (Exception e) {
            log.error("发布用户上线事件失败: userId={}", userId, e);
        }
    }
    
    /**
     * 发布用户离线事件
     */
    public void publishUserOfflineEvent(String userId) {
        try {
            UserOfflineEvent event = new UserOfflineEvent(this, userId);
            eventPublisher.publishEvent(event);
            log.debug("发布用户离线事件: userId={}", userId);
        } catch (Exception e) {
            log.error("发布用户离线事件失败: userId={}", userId, e);
        }
    }
}
package org.example.easychat.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 特别关心事件监听器
 * 处理各种特别关心相关的事件
 */
@Slf4j
@Component
public class AttentionEventListener {
    
    @Autowired
    private com.corundumstudio.socketio.SocketIOServer socketIOServer;
    
    /**
     * 处理用户状态变化事件
     */
    @EventListener
    @Async("notificationExecutor")
    public void handleUserStatusChangeEvent(UserStatusChangeEvent event) {
        try {
            log.debug("处理用户状态变化事件: userId={}, oldStatus={}, newStatus={}", 
                    event.getUserId(), event.getOldStatus(), event.getNewStatus());
            
            // 通过Socket.IO广播状态变化
            org.example.easychat.dto.UserStatusChangeEvent statusChangeEvent = new org.example.easychat.dto.UserStatusChangeEvent();
            statusChangeEvent.setUserId(event.getUserId());
            statusChangeEvent.setStatus(event.getNewStatus());
            statusChangeEvent.setPreviousStatus(event.getOldStatus());
            statusChangeEvent.setTimestamp(System.currentTimeMillis());
            
            socketIOServer.getBroadcastOperations().sendEvent("user_status_change", statusChangeEvent);
            
        } catch (Exception e) {
            log.error("处理用户状态变化事件失败: userId={}", event.getUserId(), e);
        }
    }
    
    /**
     * 处理特别关心更新事件
     */
    @EventListener
    @Async("notificationExecutor")
    public void handleAttentionUpdateEvent(AttentionUpdateEvent event) {
        try {
            log.debug("处理特别关心更新事件: userId={}, targetUserId={}, operation={}", 
                    event.getUserId(), event.getTargetUserId(), event.getOperation());
            
            // 通过Socket.IO发送特别关心更新事件
            org.example.easychat.dto.AttentionUpdateEvent updateEvent = new org.example.easychat.dto.AttentionUpdateEvent();
            updateEvent.setUserId(event.getUserId());
            updateEvent.setTargetUserId(event.getTargetUserId());
            updateEvent.setEventType(event.getOperation());
            updateEvent.setData(null);
            
            socketIOServer.getBroadcastOperations().sendEvent("attention_update", updateEvent);
            
        } catch (Exception e) {
            log.error("处理特别关心更新事件失败: userId={}, targetUserId={}", 
                    event.getUserId(), event.getTargetUserId(), e);
        }
    }
    
    /**
     * 处理用户上线事件
     */
    @EventListener
    @Async("notificationExecutor")
    public void handleUserOnlineEvent(UserOnlineEvent event) {
        try {
            log.debug("处理用户上线事件: userId={}", event.getUserId());
            
            // 通过Socket.IO广播用户上线事件
            org.example.easychat.dto.AttentionUserOnlineEvent onlineEvent = new org.example.easychat.dto.AttentionUserOnlineEvent();
            onlineEvent.setUserId(event.getUserId());
            onlineEvent.setTimestamp(String.valueOf(System.currentTimeMillis()));
            
            socketIOServer.getBroadcastOperations().sendEvent("user_online", onlineEvent);
            
        } catch (Exception e) {
            log.error("处理用户上线事件失败: userId={}", event.getUserId(), e);
        }
    }
    
    /**
     * 处理用户离线事件
     */
    @EventListener
    @Async("notificationExecutor")
    public void handleUserOfflineEvent(UserOfflineEvent event) {
        try {
            log.debug("处理用户离线事件: userId={}", event.getUserId());
            
            // 通过Socket.IO广播用户离线事件
            org.example.easychat.dto.AttentionUserOfflineEvent offlineEvent = new org.example.easychat.dto.AttentionUserOfflineEvent();
            offlineEvent.setUserId(event.getUserId());
            offlineEvent.setTimestamp(String.valueOf(System.currentTimeMillis()));
            
            socketIOServer.getBroadcastOperations().sendEvent("user_offline", offlineEvent);
            
        } catch (Exception e) {
            log.error("处理用户离线事件失败: userId={}", event.getUserId(), e);
        }
    }
}
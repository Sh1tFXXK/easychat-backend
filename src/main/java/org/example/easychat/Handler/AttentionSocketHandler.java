package org.example.easychat.Handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import lombok.extern.slf4j.Slf4j;
import org.example.easychat.Entity.SpecialAttention;
import org.example.easychat.Entity.User;
import org.example.easychat.Mapper.SpecialAttentionMapper;
import org.example.easychat.dto.*;
import org.example.easychat.service.NotificationService;
import org.example.easychat.service.SpecialAttentionService;
import org.example.easychat.service.UserService;
import org.example.easychat.service.UserStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class AttentionSocketHandler {

    @Autowired
    private UserStatusService userStatusService;

    @Autowired
    private SocketIOServer socketIOServer;

    @Autowired
    private UserService userService;
    
    @Autowired
    private SpecialAttentionService specialAttentionService;
    
    @Autowired
    private SpecialAttentionMapper specialAttentionMapper;
    
    @Autowired
    private org.example.easychat.event.AttentionEventPublisher eventPublisher;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String USER_SESSION_KEY = "user:session:";
    
    /**
     * 特别关心更新事件
     */
    @OnEvent("attentionUpdate")
    public void onAttentionUpdate(SocketIOClient client, AttentionUpdateEvent event) {
        String userId = getUserIdFromClient(client);

        // 验证权限
        if (!validateEventPermission(client, userId, event.getUserId())) {
            return;
        }

        try {
            // 验证事件数据
            if (!validateAttentionUpdateEvent(event)) {
                client.sendEvent("error", "事件数据格式错误");
                return;
            }
            
            // 广播给目标用户
            broadcastAttentionUpdate(event);
            
            // 发送确认响应
            client.sendEvent("attentionUpdateAck", createAckEvent(event.getEventType(), true));

            log.info("特别关心更新事件: userId={}, targetUserId={}, eventType={}", 
                    event.getUserId(), event.getTargetUserId(), event.getEventType());
        } catch (Exception e) {
            log.error("处理特别关心更新事件失败", e);
            client.sendEvent("error", "处理失败: " + e.getMessage());
            client.sendEvent("attentionUpdateAck", createAckEvent(event.getEventType(), false));
        }
    }
    /**
     * 用户状态变化事件
     */
    @OnEvent("userStatusChange")
    public void onUserStatusChange(SocketIOClient client, UserStatusChangeEvent event) {
        String userId = getUserIdFromClient(client);

        // 验证权限
        if (!validateEventPermission(client, userId, event.getUserId())) {
            return;
        }

        try {
            // 验证状态值
            if (!validateStatusValue(event.getStatus())) {
                client.sendEvent("error", "无效的状态值");
                return;
            }
            
            // 获取之前的状态
            org.example.easychat.Entity.UserStatus currentStatus = userStatusService.getUserStatus(event.getUserId());
            String previousStatus = currentStatus != null ? currentStatus.getStatus() : null;
            event.setPreviousStatus(previousStatus);
            event.setTimestamp(System.currentTimeMillis());
            
            // 更新用户状态
            userStatusService.updateUserStatus(event.getUserId(), event.getStatus());
            
            // 发送确认响应
            client.sendEvent("statusChangeAck", createStatusChangeAck(event.getStatus(), true));

            log.info("用户状态变化: userId={}, status={}, previousStatus={}", 
                    event.getUserId(), event.getStatus(), previousStatus);
        } catch (Exception e) {
            log.error("处理用户状态变化事件失败", e);
            client.sendEvent("error", "处理失败: " + e.getMessage());
            client.sendEvent("statusChangeAck", createStatusChangeAck(event.getStatus(), false));
        }
    }
    /**
     * 心跳检测事件
     */
    @OnEvent("heartbeat")
    public void onHeartbeat(SocketIOClient client, Object data) {
        String userId = getUserIdFromClient(client);
        if (StringUtils.isNotBlank(userId)) {
            try {
                // 更新最后活跃时间
                userStatusService.updateLastActiveTime(userId);
                
                // 发送心跳响应
                client.sendEvent("heartbeatAck", createHeartbeatAck());
                
                log.debug("心跳检测: userId={}", userId);
            } catch (Exception e) {
                log.error("处理心跳检测失败: userId={}", userId, e);
            }
        }
    }
    
    /**
     * 获取在线用户列表事件
     */
    @OnEvent("getOnlineUsers")
    public void onGetOnlineUsers(SocketIOClient client, Object data) {
        String userId = getUserIdFromClient(client);
        if (StringUtils.isBlank(userId)) {
            client.sendEvent("error", "用户身份验证失败");
            return;
        }
        
        try {
            // 获取用户的特别关心列表中的在线用户
            List<String> attentionUserIds = specialAttentionMapper.selectList(
                new LambdaQueryWrapper<SpecialAttention>()
                    .eq(SpecialAttention::getUserId, userId)
            ).stream().map(SpecialAttention::getTargetUserId).collect(java.util.stream.Collectors.toList());
            
            // 获取在线状态
            List<org.example.easychat.dto.OnlineUserInfo> onlineUsers = userStatusService.getOnlineUsersFromList(attentionUserIds);
            
            client.sendEvent("onlineUsersList", onlineUsers);
            
            log.info("获取在线用户列表: userId={}, count={}", userId, onlineUsers.size());
        } catch (Exception e) {
            log.error("获取在线用户列表失败: userId={}", userId, e);
            client.sendEvent("error", "获取在线用户列表失败");
        }
    }
    
    /**
     * 发送特别关心用户上线通知
     */
    public void sendAttentionUserOnlineNotification(String userId, String targetUserId) {
        try {
            // 获取用户信息
            User user = userService.getUserById(targetUserId);
            if (user == null) {
                return;
            }

            AttentionUserOnlineEvent event = new AttentionUserOnlineEvent();
            event.setUserId(targetUserId);
            event.setUserName(user.getNickName());
            event.setAvatar(user.getAvatar());
            event.setTimestamp(LocalDateTime.now().toString());

            // 发送给特定用户
            socketIOServer.getRoomOperations("user:" + userId)
                    .sendEvent("attentionUserOnline", event);

            log.info("发送特别关心用户上线通知: userId={}, targetUserId={}", userId, targetUserId);
        } catch (Exception e) {
            log.error("发送特别关心用户上线通知失败", e);
        }
    }
    /**
     * 发送特别关心用户离线通知
     */
    public void sendAttentionUserOfflineNotification(String userId, String targetUserId) {
        try {
            // 获取用户信息
            User user = userService.getUserById(targetUserId);
            if (user == null) {
                return;
            }

            AttentionUserOfflineEvent event = new AttentionUserOfflineEvent();
            event.setUserId(targetUserId);
            event.setUserName(user.getNickName());
            event.setLastActiveTime(LocalDateTime.now().toString());
            event.setTimestamp(LocalDateTime.now().toString());

            // 发送给特定用户
            socketIOServer.getRoomOperations("user:" + userId)
                    .sendEvent("attentionUserOffline", event);

            log.info("发送特别关心用户离线通知: userId={}, targetUserId={}", userId, targetUserId);
        } catch (Exception e) {
            log.error("发送特别关心用户离线通知失败", e);
        }
    }
    
    /**
     * 发送特别关心用户状态变化通知
     */
    public void sendAttentionUserStatusChangeNotification(String userId, String targetUserId, String newStatus, String previousStatus) {
        try {
            // 获取用户信息
            User user = userService.getUserById(targetUserId);
            if (user == null) {
                return;
            }

            AttentionStatusChangeEvent event = new AttentionStatusChangeEvent();
            event.setUserId(targetUserId);
            event.setUserName(user.getNickName());
            event.setAvatar(user.getAvatar());
            event.setNewStatus(newStatus);
            event.setPreviousStatus(previousStatus);
            event.setTimestamp(LocalDateTime.now().toString());

            // 发送给特定用户
            socketIOServer.getRoomOperations("user:" + userId)
                    .sendEvent("attentionUserStatusChange", event);

            log.info("发送特别关心用户状态变化通知: userId={}, targetUserId={}, newStatus={}", 
                    userId, targetUserId, newStatus);
        } catch (Exception e) {
            log.error("发送特别关心用户状态变化通知失败", e);
        }
    }
    
    /**
     * 发送特别关心消息通知
     */
    public void sendAttentionMessageNotification(String userId, String targetUserId, String messageContent, String messageId) {
        try {
            // 获取用户信息
            User user = userService.getUserById(targetUserId);
            if (user == null) {
                return;
            }

            AttentionMessageEvent event = new AttentionMessageEvent();
            event.setUserId(targetUserId);
            event.setUserName(user.getNickName());
            event.setAvatar(user.getAvatar());
            event.setMessagePreview(messageContent.length() > 100 ? 
                    messageContent.substring(0, 100) + "..." : messageContent);
            event.setMessageId(messageId);
            event.setTimestamp(LocalDateTime.now().toString());

            // 发送给特定用户
            socketIOServer.getRoomOperations("user:" + userId)
                    .sendEvent("attentionMessage", event);

            log.info("发送特别关心消息通知: userId={}, targetUserId={}, messageId={}", 
                    userId, targetUserId, messageId);
        } catch (Exception e) {
            log.error("发送特别关心消息通知失败", e);
        }
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 从客户端获取用户ID
     */
    private String getUserIdFromClient(SocketIOClient client) {
        // 直接从已认证的客户端获取用户ID（由ChatSocketIOHandler在认证时设置）
        return client.get("userId");
    }
    
    /**
     * 验证用户身份认证
     */
    private boolean validateUserAuthentication(SocketIOClient client, String userId) {
        try {
            // 验证用户是否存在
            User user = userService.getUserById(userId);
            if (user == null) {
                log.warn("用户不存在: userId={}", userId);
                return false;
            }
            
            // 验证token（如果有的话）
            String token = client.getHandshakeData().getSingleUrlParam("token");
            if (StringUtils.isNotBlank(token)) {
                // 这里应该验证JWT token
                // return jwtUtil.validateToken(token, userId);
            }
            
            return true;
        } catch (Exception e) {
            log.error("验证用户身份失败: userId={}", userId, e);
            return false;
        }
    }
    
    /**
     * 验证事件权限
     */
    private boolean validateEventPermission(SocketIOClient client, String clientUserId, String eventUserId) {
        if (!clientUserId.equals(eventUserId)) {
            client.sendEvent("error", "权限不足：无法操作其他用户的数据");
            log.warn("权限验证失败: clientUserId={}, eventUserId={}", clientUserId, eventUserId);
            return false;
        }
        return true;
    }
    
    /**
     * 验证特别关心更新事件
     */
    private boolean validateAttentionUpdateEvent(AttentionUpdateEvent event) {
        if (StringUtils.isBlank(event.getUserId()) || 
            StringUtils.isBlank(event.getTargetUserId()) ||
            StringUtils.isBlank(event.getEventType())) {
            return false;
        }
        
        // 验证事件类型
        return "add".equals(event.getEventType()) || 
               "remove".equals(event.getEventType()) || 
               "update".equals(event.getEventType());
    }
    
    /**
     * 验证状态值
     */
    private boolean validateStatusValue(String status) {
        return "online".equals(status) || 
               "offline".equals(status) || 
               "away".equals(status) || 
               "busy".equals(status);
    }
    
    /**
     * 广播特别关心更新
     */
    private void broadcastAttentionUpdate(AttentionUpdateEvent event) {
        CompletableFuture.runAsync(() -> {
            try {
                // 发送给目标用户
                socketIOServer.getRoomOperations("user:" + event.getTargetUserId())
                        .sendEvent("attentionUpdated", event);
                
                // 如果是添加操作，也通知操作者
                if ("add".equals(event.getEventType())) {
                    socketIOServer.getRoomOperations("user:" + event.getUserId())
                            .sendEvent("attentionAdded", event);
                }
            } catch (Exception e) {
                log.error("广播特别关心更新失败", e);
            }
        });
    }
    
    /**
     * 存储用户会话信息
     */
    private void storeUserSession(String userId, String sessionId) {
        try {
            redisTemplate.opsForValue().set(USER_SESSION_KEY + userId, sessionId, 
                    java.time.Duration.ofHours(24));
        } catch (Exception e) {
            log.error("存储用户会话信息失败: userId={}", userId, e);
        }
    }
    
    /**
     * 清理用户会话信息
     */
    private void clearUserSession(String userId) {
        try {
            redisTemplate.delete(USER_SESSION_KEY + userId);
        } catch (Exception e) {
            log.error("清理用户会话信息失败: userId={}", userId, e);
        }
    }
    
    /**
     * 创建连接事件
     */
    private Object createConnectionEvent(String userId, String status) {
        return new Object() {
            public String getUserId() { return userId; }
            public String getStatus() { return status; }
            public String getTimestamp() { return LocalDateTime.now().toString(); }
        };
    }
    
    /**
     * 创建确认事件
     */
    private Object createAckEvent(String eventType, boolean success) {
        return new Object() {
            public String getEventType() { return eventType; }
            public boolean isSuccess() { return success; }
            public String getTimestamp() { return LocalDateTime.now().toString(); }
        };
    }
    
    /**
     * 创建状态变化确认
     */
    private Object createStatusChangeAck(String status, boolean success) {
        return new Object() {
            public String getStatus() { return status; }
            public boolean isSuccess() { return success; }
            public String getTimestamp() { return LocalDateTime.now().toString(); }
        };
    }
    
    /**
     * 创建心跳确认
     */
    private Object createHeartbeatAck() {
        return new Object() {
            public String getStatus() { return "alive"; }
            public String getTimestamp() { return LocalDateTime.now().toString(); }
        };
    }
}
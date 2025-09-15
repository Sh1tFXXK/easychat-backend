package org.example.easychat.service;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.Entity.AttentionNotification;
import org.example.easychat.Entity.User;
import org.example.easychat.Mapper.AttentionNotificationMapper;
import org.example.easychat.Mapper.UserMapper;
import org.example.easychat.dto.AttentionUpdateEvent;
import org.example.easychat.dto.AttentionUserOnlineEvent;
import org.example.easychat.dto.NotificationMessage;
import org.example.easychat.dto.NotificationPriority;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 通知服务 - 完整实现
 * 包含消息队列、重试机制、通知模板等功能
 */
@Slf4j
@Service
public class NotificationService {
    
    @Autowired
    private AttentionNotificationMapper notificationMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private com.corundumstudio.socketio.SocketIOServer socketIOServer;
    
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;
    
    @Autowired
    private org.example.easychat.event.AttentionEventPublisher eventPublisher;
    
    // 队列名称常量
    private static final String NOTIFICATION_QUEUE = "attention.notification.queue";
    private static final String NOTIFICATION_EXCHANGE = "attention.notification.exchange";
    private static final String NOTIFICATION_ROUTING_KEY = "attention.notification";
    private static final String HIGH_PRIORITY_ROUTING_KEY = "attention.notification.high";
    
    // 缓存键常量
    private static final String NOTIFICATION_TEMPLATE_KEY = "notification:template:";
    private static final String USER_NOTIFICATION_SETTINGS_KEY = "user:notification:settings:";
    private static final String NOTIFICATION_RETRY_KEY = "notification:retry:";
    
    // 通知模板
    private static final Map<String, String> NOTIFICATION_TEMPLATES = new HashMap<>();
    
    static {
        NOTIFICATION_TEMPLATES.put("online", "{userName} 已上线");
        NOTIFICATION_TEMPLATES.put("offline", "{userName} 已离线");
        NOTIFICATION_TEMPLATES.put("status_change", "{userName} 状态变更为: {status}");
        NOTIFICATION_TEMPLATES.put("message", "特别关心用户 {userName} 发来消息: {messagePreview}");
        NOTIFICATION_TEMPLATES.put("attention_added", "您被 {userName} 添加为特别关心");
        NOTIFICATION_TEMPLATES.put("attention_removed", "您被 {userName} 取消特别关心");
    }
    
    /**
     * 发送特别关心更新事件
     */
    public void sendAttentionUpdateEvent(String userId, String targetUserId, String operation) {
        try {
            // 使用事件发布器发布事件，避免循环依赖
            eventPublisher.publishAttentionUpdateEvent(userId, targetUserId, operation);
            
            // 同时发送到消息队列进行异步处理
            org.example.easychat.dto.AttentionUpdateEvent event = new org.example.easychat.dto.AttentionUpdateEvent();
            event.setUserId(userId);
            event.setTargetUserId(targetUserId);
            event.setEventType(operation);
            event.setData(null);
            
            NotificationMessage message = new NotificationMessage();
            message.setType("attention_update");
            message.setUserId(targetUserId);
            message.setTargetUserId(userId);
            message.setData(event);
            message.setTimestamp(LocalDateTime.now());
            
            sendToQueue(message);
            
            log.info("发送特别关心更新事件: userId={}, targetUserId={}, operation={}", 
                    userId, targetUserId, operation);
            
        } catch (Exception e) {
            log.error("发送特别关心更新事件失败: userId={}, targetUserId={}", userId, targetUserId, e);
            // 失败时直接发送Socket.IO事件作为降级处理
            try {
                socketIOServer.getBroadcastOperations().sendEvent("attention_update", 
                    createAttentionUpdateEventData(userId, targetUserId, operation));
            } catch (Exception socketError) {
                log.error("Socket.IO降级处理也失败", socketError);
            }
        }
    }
    
    /**
     * 发送用户上线通知
     */
    public void sendOnlineNotification(String userId, String targetUserId) {
        try {
            // 检查用户通知设置
            if (!isNotificationEnabled(userId, "online")) {
                log.debug("用户 {} 已关闭上线通知", userId);
                return;
            }
            
            // 获取目标用户信息
            User targetUser = userMapper.selectById(targetUserId);
            if (targetUser == null) {
                log.warn("目标用户不存在: {}", targetUserId);
                return;
            }
            
            // 使用模板生成通知内容
            String content = generateNotificationContent("online", targetUser, null);
            
            // 创建通知消息
            NotificationMessage message = new NotificationMessage();
            message.setType("online");
            message.setUserId(userId);
            message.setTargetUserId(targetUserId);
            message.setContent(content);
            message.setTimestamp(LocalDateTime.now());
            message.setData(createOnlineEventData(targetUser));
            
            // 发送到消息队列
            sendToQueue(message);
            
            log.info("发送用户上线通知到队列: userId={}, targetUserId={}", userId, targetUserId);
            
        } catch (Exception e) {
            log.error("发送用户上线通知失败: userId={}, targetUserId={}", userId, targetUserId, e);
            // 降级处理：直接发送Socket.IO通知
            fallbackDirectNotification(userId, targetUserId, "online");
        }
    }
    
    /**
     * 发送用户离线通知
     */
    public void sendOfflineNotification(String userId, String targetUserId) {
        try {
            // 检查用户通知设置
            if (!isNotificationEnabled(userId, "offline")) {
                log.debug("用户 {} 已关闭离线通知", userId);
                return;
            }
            
            // 获取目标用户信息
            User targetUser = userMapper.selectById(targetUserId);
            if (targetUser == null) {
                log.warn("目标用户不存在: {}", targetUserId);
                return;
            }
            
            // 使用模板生成通知内容
            String content = generateNotificationContent("offline", targetUser, null);
            
            // 创建通知消息
            NotificationMessage message = new NotificationMessage();
            message.setType("offline");
            message.setUserId(userId);
            message.setTargetUserId(targetUserId);
            message.setContent(content);
            message.setTimestamp(LocalDateTime.now());
            message.setData(createOfflineEventData(targetUser));
            
            // 发送到消息队列
            sendToQueue(message);
            
            log.info("发送用户离线通知到队列: userId={}, targetUserId={}", userId, targetUserId);
            
        } catch (Exception e) {
            log.error("发送用户离线通知失败: userId={}, targetUserId={}", userId, targetUserId, e);
            // 降级处理：直接发送Socket.IO通知
            fallbackDirectNotification(userId, targetUserId, "offline");
        }
    }
    
    /**
     * 发送状态变化通知
     */
    public void sendStatusChangeNotification(String userId, String targetUserId, String newStatus) {
        try {
            // 检查用户通知设置
            if (!isNotificationEnabled(userId, "status_change")) {
                log.debug("用户 {} 已关闭状态变化通知", userId);
                return;
            }
            
            // 获取目标用户信息
            User targetUser = userMapper.selectById(targetUserId);
            if (targetUser == null) {
                log.warn("目标用户不存在: {}", targetUserId);
                return;
            }
            
            // 使用模板生成通知内容
            Map<String, String> params = new HashMap<>();
            params.put("status", getStatusDisplayName(newStatus));
            String content = generateNotificationContent("status_change", targetUser, params);
            
            // 创建通知消息
            NotificationMessage message = new NotificationMessage();
            message.setType("status_change");
            message.setUserId(userId);
            message.setTargetUserId(targetUserId);
            message.setContent(content);
            message.setTimestamp(LocalDateTime.now());
            message.setData(createStatusChangeEventData(targetUser, newStatus));
            
            // 发送到消息队列
            sendToQueue(message);
            
            log.info("发送状态变化通知到队列: userId={}, targetUserId={}, newStatus={}", userId, targetUserId, newStatus);
            
        } catch (Exception e) {
            log.error("发送状态变化通知失败: userId={}, targetUserId={}", userId, targetUserId, e);
            // 降级处理：直接发送Socket.IO通知
            fallbackDirectNotification(userId, targetUserId, "status_change", newStatus);
        }
    }
    
    /**
     * 发送消息优先通知（重载方法 - 兼容性）
     */
    public void sendMessageNotification(String userId, String targetUserId) {
        sendMessageNotification(userId, targetUserId, "新消息");
    }
    
    /**
     * 发送消息优先通知
     */
    public void sendMessageNotification(String userId, String targetUserId, String messageContent) {
        try {
            // 检查用户通知设置
            if (!isNotificationEnabled(userId, "message")) {
                log.debug("用户 {} 已关闭消息通知", userId);
                return;
            }
            
            // 获取目标用户信息
            User targetUser = userMapper.selectById(targetUserId);
            if (targetUser == null) {
                log.warn("目标用户不存在: {}", targetUserId);
                return;
            }
            
            // 使用模板生成通知内容
            Map<String, String> params = new HashMap<>();
            params.put("messagePreview", messageContent.length() > 50 ? 
                messageContent.substring(0, 50) + "..." : messageContent);
            String content = generateNotificationContent("message", targetUser, params);
            
            // 创建通知消息（高优先级）
            NotificationMessage message = new NotificationMessage();
            message.setType("message");
            message.setUserId(userId);
            message.setTargetUserId(targetUserId);
            message.setContent(content);
            message.setTimestamp(LocalDateTime.now());
            message.setPriority(NotificationPriority.HIGH);
            message.setData(createMessageEventData(targetUser, messageContent));
            
            // 发送到消息队列
            sendToQueue(message);
            
            log.info("发送消息优先通知到队列: userId={}, targetUserId={}", userId, targetUserId);
            
        } catch (Exception e) {
            log.error("发送消息优先通知失败: userId={}, targetUserId={}", userId, targetUserId, e);
            // 降级处理：直接发送Socket.IO通知
            fallbackDirectNotification(userId, targetUserId, "message", messageContent);
        }
    }
    
    /**
     * 发送到消息队列
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private void sendToQueue(NotificationMessage message) {
        // 如果RabbitMQ不可用，直接降级到Socket.IO处理
        if (rabbitTemplate == null) {
            log.debug("RabbitMQ不可用，直接处理通知消息: type={}, userId={}", 
                    message.getType(), message.getUserId());
            processNotificationMessage(message);
            return;
        }
        
        try {
            // 根据优先级选择不同的路由键
            String routingKey = message.getPriority().isHigherThan(NotificationPriority.NORMAL) ?
                HIGH_PRIORITY_ROUTING_KEY : NOTIFICATION_ROUTING_KEY;
            
            // 设置消息优先级
            rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, routingKey, message, msg -> {
                msg.getMessageProperties().setPriority(message.getPriority().getLevel());
                return msg;
            });
            
            log.debug("消息已发送到队列: type={}, userId={}, priority={}", 
                    message.getType(), message.getUserId(), message.getPriority());
        } catch (Exception e) {
            log.error("发送消息到队列失败: {}", message, e);
            // 记录失败的通知，用于重试
            recordFailedNotification(message, e.getMessage());
            throw e;
        }
    }
    
    /**
     * 处理队列中的通知消息
     */
    public void processNotificationMessage(NotificationMessage message) {
        try {
            // 保存通知记录到数据库
            saveNotificationRecord(message);
            
            // 发送Socket.IO通知
            sendSocketNotificationWithRetry(message);
            
            log.info("处理通知消息成功: type={}, userId={}", message.getType(), message.getUserId());
            
        } catch (Exception e) {
            log.error("处理通知消息失败: {}", message, e);
            // 记录失败的通知，用于重试
            recordFailedNotification(message, e.getMessage());
        }
    }
    
    /**
     * 带重试的Socket.IO通知发送
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 500))
    private void sendSocketNotificationWithRetry(NotificationMessage message) {
        try {
            String eventName = getEventNameByType(message.getType());
            socketIOServer.getRoomOperations("user:" + message.getUserId())
                .sendEvent(eventName, message.getData());
            
            log.debug("Socket.IO通知发送成功: userId={}, eventName={}", message.getUserId(), eventName);
            
        } catch (Exception e) {
            log.error("Socket.IO通知发送失败: userId={}, type={}", message.getUserId(), message.getType(), e);
            throw e;
        }
    }
    
    /**
     * 直接发送Socket.IO通知（降级处理）
     */
    private void sendSocketNotificationDirect(String userId, String eventName, Object eventData) {
        CompletableFuture.runAsync(() -> {
            try {
                socketIOServer.getRoomOperations("user:" + userId)
                    .sendEvent(eventName, eventData);
                
                log.debug("直接发送Socket.IO通知: userId={}, eventName={}", userId, eventName);
            } catch (Exception e) {
                log.error("直接发送Socket.IO通知失败: userId={}, eventName={}", userId, eventName, e);
            }
        });
    }
    
    /**
     * 保存通知记录到数据库
     */
    private void saveNotificationRecord(NotificationMessage message) {
        try {
            AttentionNotification notification = new AttentionNotification();
            notification.setUserId(message.getUserId());
            notification.setTargetUserId(message.getTargetUserId());
            notification.setNotificationType(message.getType());
            notification.setContent(message.getContent());
            notification.setIsRead(false);
            notification.setCreateTime(message.getTimestamp());
            
            notificationMapper.insert(notification);
            
        } catch (Exception e) {
            log.error("保存通知记录失败: {}", message, e);
            throw e;
        }
    }
    
    /**
     * 记录失败的通知，用于重试
     */
    private void recordFailedNotification(NotificationMessage message, String errorMessage) {
        try {
            String retryKey = NOTIFICATION_RETRY_KEY + System.currentTimeMillis();
            Map<String, Object> retryData = new HashMap<>();
            retryData.put("message", message);
            retryData.put("error", errorMessage);
            retryData.put("retryCount", 0);
            retryData.put("nextRetryTime", System.currentTimeMillis() + 60000); // 1分钟后重试
            
            redisTemplate.opsForValue().set(retryKey, retryData, 24, TimeUnit.HOURS);
            
            log.info("记录失败通知用于重试: key={}", retryKey);
            
        } catch (Exception e) {
            log.error("记录失败通知异常", e);
        }
    }
    
    /**
     * 重试失败的通知
     */
    public void retryFailedNotifications() {
        try {
            String pattern = NOTIFICATION_RETRY_KEY + "*";
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                try (org.springframework.data.redis.core.Cursor<byte[]> cursor = 
                     connection.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                         .match(pattern).count(100).build())) {
                    
                    while (cursor.hasNext()) {
                        String key = new String(cursor.next());
                        processRetryNotification(key);
                    }
                } catch (Exception e) {
                    log.error("扫描重试通知失败", e);
                }
                return null;
            });
            
        } catch (Exception e) {
            log.error("重试失败通知异常", e);
        }
    }
    
    /**
     * 处理单个重试通知
     */
    @SuppressWarnings("unchecked")
    private void processRetryNotification(String retryKey) {
        try {
            Map<String, Object> retryData = (Map<String, Object>) redisTemplate.opsForValue().get(retryKey);
            if (retryData == null) {
                return;
            }
            
            long nextRetryTime = (Long) retryData.get("nextRetryTime");
            if (System.currentTimeMillis() < nextRetryTime) {
                return; // 还未到重试时间
            }
            
            int retryCount = (Integer) retryData.get("retryCount");
            if (retryCount >= 3) {
                // 超过最大重试次数，删除记录
                redisTemplate.delete(retryKey);
                log.warn("通知重试次数超限，放弃重试: key={}", retryKey);
                return;
            }
            
            NotificationMessage message = (NotificationMessage) retryData.get("message");
            
            try {
                // 重新处理通知
                processNotificationMessage(message);
                
                // 成功后删除重试记录
                redisTemplate.delete(retryKey);
                log.info("通知重试成功: key={}", retryKey);
                
            } catch (Exception e) {
                // 重试失败，更新重试信息
                retryData.put("retryCount", retryCount + 1);
                retryData.put("nextRetryTime", System.currentTimeMillis() + (60000 * (retryCount + 1))); // 递增重试间隔
                retryData.put("lastError", e.getMessage());
                
                redisTemplate.opsForValue().set(retryKey, retryData, 24, TimeUnit.HOURS);
                log.warn("通知重试失败，将再次重试: key={}, retryCount={}", retryKey, retryCount + 1);
            }
            
        } catch (Exception e) {
            log.error("处理重试通知异常: key={}", retryKey, e);
        }
    }
    
    /**
     * 检查用户通知设置
     */
    private boolean isNotificationEnabled(String userId, String notificationType) {
        try {
            String settingsKey = USER_NOTIFICATION_SETTINGS_KEY + userId;
            @SuppressWarnings("unchecked")
            Map<String, Boolean> settings = (Map<String, Boolean>) redisTemplate.opsForValue().get(settingsKey);
            
            if (settings == null) {
                // 默认开启所有通知
                return true;
            }
            
            return settings.getOrDefault(notificationType, true);
            
        } catch (Exception e) {
            log.error("检查用户通知设置失败: userId={}, type={}", userId, notificationType, e);
            return true; // 异常时默认开启
        }
    }
    
    /**
     * 更新用户通知设置
     */
    public void updateUserNotificationSettings(String userId, Map<String, Boolean> settings) {
        try {
            String settingsKey = USER_NOTIFICATION_SETTINGS_KEY + userId;
            redisTemplate.opsForValue().set(settingsKey, settings, 30, TimeUnit.DAYS);
            
            log.info("更新用户通知设置: userId={}, settings={}", userId, settings);
            
        } catch (Exception e) {
            log.error("更新用户通知设置失败: userId={}", userId, e);
        }
    }
    
    /**
     * 使用模板生成通知内容
     */
    private String generateNotificationContent(String type, User user, Map<String, String> params) {
        try {
            String template = getNotificationTemplate(type);
            if (template == null) {
                return "您有新的通知";
            }
            
            // 替换用户名
            String content = template.replace("{userName}", user.getNickName());
            
            // 替换其他参数
            if (params != null) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    content = content.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
            
            return content;
            
        } catch (Exception e) {
            log.error("生成通知内容失败: type={}, userId={}", type, user.getId(), e);
            return "您有新的通知";
        }
    }
    
    /**
     * 获取通知模板
     */
    private String getNotificationTemplate(String type) {
        try {
            // 先从缓存获取自定义模板
            String templateKey = NOTIFICATION_TEMPLATE_KEY + type;
            String customTemplate = (String) redisTemplate.opsForValue().get(templateKey);
            
            if (customTemplate != null) {
                return customTemplate;
            }
            
            // 使用默认模板
            return NOTIFICATION_TEMPLATES.get(type);
            
        } catch (Exception e) {
            log.error("获取通知模板失败: type={}", type, e);
            return NOTIFICATION_TEMPLATES.get(type);
        }
    }
    
    /**
     * 设置自定义通知模板
     */
    public void setNotificationTemplate(String type, String template) {
        try {
            String templateKey = NOTIFICATION_TEMPLATE_KEY + type;
            redisTemplate.opsForValue().set(templateKey, template, 30, TimeUnit.DAYS);
            
            log.info("设置自定义通知模板: type={}, template={}", type, template);
            
        } catch (Exception e) {
            log.error("设置通知模板失败: type={}", type, e);
        }
    }
    
    /**
     * 降级处理：直接发送通知
     */
    private void fallbackDirectNotification(String userId, String targetUserId, String type, String... params) {
        CompletableFuture.runAsync(() -> {
            try {
                User targetUser = userMapper.selectById(targetUserId);
                if (targetUser == null) {
                    return;
                }
                
                // 创建通知记录
                AttentionNotification notification = new AttentionNotification();
                notification.setUserId(userId);
                notification.setTargetUserId(targetUserId);
                notification.setNotificationType(type);
                notification.setContent(generateFallbackContent(type, targetUser, params));
                notification.setIsRead(false);
                notification.setCreateTime(LocalDateTime.now());
                
                notificationMapper.insert(notification);
                
                // 发送Socket.IO通知
                Object eventData = createEventDataByType(type, targetUser, params);
                String eventName = getEventNameByType(type);
                sendSocketNotificationDirect(userId, eventName, eventData);
                
                log.info("降级处理通知成功: userId={}, type={}", userId, type);
                
            } catch (Exception e) {
                log.error("降级处理通知失败: userId={}, type={}", userId, type, e);
            }
        });
    }
    
    /**
     * 生成降级内容
     */
    private String generateFallbackContent(String type, User user, String... params) {
        switch (type) {
            case "online":
                return user.getNickName() + " 已上线";
            case "offline":
                return user.getNickName() + " 已离线";
            case "status_change":
                return user.getNickName() + " 状态变更为: " + (params.length > 0 ? getStatusDisplayName(params[0]) : "未知");
            case "message":
                return "特别关心用户 " + user.getNickName() + " 发来消息" + 
                    (params.length > 0 ? ": " + (params[0].length() > 50 ? params[0].substring(0, 50) + "..." : params[0]) : "");
            default:
                return "您有新的通知";
        }
    }
    
    /**
     * 根据类型创建事件数据
     */
    private Object createEventDataByType(String type, User user, String... params) {
        switch (type) {
            case "online":
                return createOnlineEventData(user);
            case "offline":
                return createOfflineEventData(user);
            case "status_change":
                return createStatusChangeEventData(user, params.length > 0 ? params[0] : "unknown");
            case "message":
                return createMessageEventData(user, params.length > 0 ? params[0] : "");
            default:
                return createDefaultEventData(user);
        }
    }
    
    /**
     * 根据类型获取事件名称
     */
    private String getEventNameByType(String type) {
        switch (type) {
            case "online":
                return "attentionUserOnline";
            case "offline":
                return "attentionUserOffline";
            case "status_change":
                return "attentionUserStatusChange";
            case "message":
                return "attentionMessage";
            case "attention_update":
                return "attentionUpdated";
            default:
                return "attentionNotification";
        }
    }
    
    /**
     * 创建特别关心更新事件数据
     */
    private Object createAttentionUpdateEventData(String userId, String targetUserId, String operation) {
        AttentionUpdateEvent event = new AttentionUpdateEvent();
        event.setUserId(userId);
        event.setTargetUserId(targetUserId);
        event.setEventType(operation);
        event.setData(null);
        return event;
    }
    
    /**
     * 创建用户上线事件数据
     */
    private Object createOnlineEventData(User user) {
        AttentionUserOnlineEvent event = new AttentionUserOnlineEvent();
        event.setUserId(user.getId());
        event.setUserName(user.getNickName());
        event.setAvatar(user.getAvatar());
        event.setTimestamp(LocalDateTime.now().toString());
        return event;
    }
    
    /**
     * 创建用户离线事件数据
     */
    private Object createOfflineEventData(User user) {
        AttentionUserOnlineEvent event = new AttentionUserOnlineEvent();
        event.setUserId(user.getId());
        event.setUserName(user.getNickName());
        event.setAvatar(user.getAvatar());
        event.setTimestamp(LocalDateTime.now().toString());
        return event;
    }
    
    /**
     * 创建状态变化事件数据
     */
    private Object createStatusChangeEventData(User user, String newStatus) {
        return new HashMap<String, Object>() {{
            put("userId", user.getId());
            put("userName", user.getNickName());
            put("avatar", user.getAvatar());
            put("newStatus", newStatus);
            put("timestamp", LocalDateTime.now().toString());
        }};
    }
    
    /**
     * 创建消息事件数据
     */
    private Object createMessageEventData(User user, String messageContent) {
        return new HashMap<String, Object>() {{
            put("userId", user.getId());
            put("userName", user.getNickName());
            put("avatar", user.getAvatar());
            put("messagePreview", messageContent.length() > 100 ? messageContent.substring(0, 100) + "..." : messageContent);
            put("timestamp", LocalDateTime.now().toString());
        }};
    }
    
    /**
     * 创建默认事件数据
     */
    private Object createDefaultEventData(User user) {
        return new HashMap<String, Object>() {{
            put("userId", user.getId());
            put("userName", user.getNickName());
            put("avatar", user.getAvatar());
            put("timestamp", LocalDateTime.now().toString());
        }};
    }
    
    /**
     * 获取状态显示名称
     */
    private String getStatusDisplayName(String status) {
        switch (status) {
            case "online": return "在线";
            case "offline": return "离线";
            case "away": return "离开";
            case "busy": return "忙碌";
            default: return status;
        }
    }
}
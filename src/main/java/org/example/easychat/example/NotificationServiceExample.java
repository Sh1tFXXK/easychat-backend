package org.example.easychat.example;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知服务使用示例
 */
@Slf4j
@Component
public class NotificationServiceExample {
    
    @Autowired
    private NotificationService notificationService;
    
    /**
     * 演示如何使用通知服务
     */
    public void demonstrateNotificationService() {
        String userId = "user123";
        String targetUserId = "target456";
        
        // 1. 发送用户上线通知
        log.info("=== 发送用户上线通知 ===");
        notificationService.sendOnlineNotification(userId, targetUserId);
        
        // 2. 发送用户离线通知
        log.info("=== 发送用户离线通知 ===");
        notificationService.sendOfflineNotification(userId, targetUserId);
        
        // 3. 发送状态变化通知
        log.info("=== 发送状态变化通知 ===");
        notificationService.sendStatusChangeNotification(userId, targetUserId, "busy");
        
        // 4. 发送消息优先通知
        log.info("=== 发送消息优先通知 ===");
        notificationService.sendMessageNotification(userId, targetUserId, "这是一条重要消息！");
        
        // 5. 发送特别关心更新事件
        log.info("=== 发送特别关心更新事件 ===");
        notificationService.sendAttentionUpdateEvent(userId, targetUserId, "add");
        
        // 6. 更新用户通知设置
        log.info("=== 更新用户通知设置 ===");
        Map<String, Boolean> settings = new HashMap<>();
        settings.put("online", true);
        settings.put("offline", false);
        settings.put("message", true);
        settings.put("status_change", true);
        notificationService.updateUserNotificationSettings(userId, settings);
        
        // 7. 设置自定义通知模板
        log.info("=== 设置自定义通知模板 ===");
        notificationService.setNotificationTemplate("online", "好友 {userName} 上线啦！快去聊天吧~");
        
        // 8. 手动重试失败的通知
        log.info("=== 手动重试失败的通知 ===");
        notificationService.retryFailedNotifications();
        
        log.info("=== 通知服务演示完成 ===");
    }
    
    /**
     * 演示高并发场景下的通知发送
     */
    public void demonstrateHighConcurrencyNotifications() {
        log.info("=== 高并发通知发送演示 ===");
        
        // 模拟100个用户同时上线
        for (int i = 1; i <= 100; i++) {
            String userId = "user" + i;
            String targetUserId = "target" + i;
            
            // 异步发送通知
            new Thread(() -> {
                try {
                    notificationService.sendOnlineNotification(userId, targetUserId);
                    Thread.sleep(10); // 模拟处理时间
                    notificationService.sendMessageNotification(userId, targetUserId, "批量消息测试 " + userId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
        
        log.info("=== 高并发通知发送演示启动完成 ===");
    }
    
    /**
     * 演示通知模板的使用
     */
    public void demonstrateNotificationTemplates() {
        log.info("=== 通知模板演示 ===");
        
        // 设置各种类型的自定义模板
        notificationService.setNotificationTemplate("online", "🟢 {userName} 现在在线");
        notificationService.setNotificationTemplate("offline", "🔴 {userName} 已离线");
        notificationService.setNotificationTemplate("status_change", "📱 {userName} 状态: {status}");
        notificationService.setNotificationTemplate("message", "💬 {userName}: {messagePreview}");
        
        // 发送使用自定义模板的通知
        String userId = "template_user";
        String targetUserId = "template_target";
        
        notificationService.sendOnlineNotification(userId, targetUserId);
        notificationService.sendStatusChangeNotification(userId, targetUserId, "away");
        notificationService.sendMessageNotification(userId, targetUserId, "这是使用自定义模板的消息");
        
        log.info("=== 通知模板演示完成 ===");
    }
    
    /**
     * 演示通知设置的个性化配置
     */
    public void demonstratePersonalizedSettings() {
        log.info("=== 个性化通知设置演示 ===");
        
        // 用户1：只接收消息通知
        Map<String, Boolean> user1Settings = new HashMap<>();
        user1Settings.put("online", false);
        user1Settings.put("offline", false);
        user1Settings.put("message", true);
        user1Settings.put("status_change", false);
        notificationService.updateUserNotificationSettings("user1", user1Settings);
        
        // 用户2：接收所有通知
        Map<String, Boolean> user2Settings = new HashMap<>();
        user2Settings.put("online", true);
        user2Settings.put("offline", true);
        user2Settings.put("message", true);
        user2Settings.put("status_change", true);
        notificationService.updateUserNotificationSettings("user2", user2Settings);
        
        // 用户3：关闭所有通知
        Map<String, Boolean> user3Settings = new HashMap<>();
        user3Settings.put("online", false);
        user3Settings.put("offline", false);
        user3Settings.put("message", false);
        user3Settings.put("status_change", false);
        notificationService.updateUserNotificationSettings("user3", user3Settings);
        
        // 测试不同用户的通知接收情况
        notificationService.sendOnlineNotification("user1", "target");  // 不会发送
        notificationService.sendMessageNotification("user1", "target"); // 会发送
        
        notificationService.sendOnlineNotification("user2", "target");  // 会发送
        notificationService.sendMessageNotification("user2", "target"); // 会发送
        
        notificationService.sendOnlineNotification("user3", "target");  // 不会发送
        notificationService.sendMessageNotification("user3", "target"); // 不会发送
        
        log.info("=== 个性化通知设置演示完成 ===");
    }
}
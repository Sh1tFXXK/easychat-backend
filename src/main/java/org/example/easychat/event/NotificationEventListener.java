package org.example.easychat.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.easychat.Entity.SpecialAttention;
import org.example.easychat.Mapper.SpecialAttentionMapper;
import org.example.easychat.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通知事件监听器
 * 处理用户状态变化相关的通知发送
 */
@Slf4j
@Component
public class NotificationEventListener {
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private SpecialAttentionMapper attentionMapper;
    
    /**
     * 处理用户状态变化事件，发送相关通知
     */
    @EventListener
    @Async("notificationExecutor")
    public void handleUserStatusChangeForNotification(UserStatusChangeEvent event) {
        try {
            String userId = event.getUserId();
            String oldStatus = event.getOldStatus();
            String newStatus = event.getNewStatus();
            
            log.debug("处理用户状态变化通知: userId={}, oldStatus={}, newStatus={}", 
                    userId, oldStatus, newStatus);
            
            // 获取关心该用户的所有用户
            List<String> attentionUsers = attentionMapper.getUsersAttentionTo(userId);
            
            if (!attentionUsers.isEmpty()) {
                for (String attentionUserId : attentionUsers) {
                    try {
                        // 检查通知设置
                        SpecialAttention attention = attentionMapper.selectOne(
                            new LambdaQueryWrapper<SpecialAttention>()
                                .eq(SpecialAttention::getUserId, attentionUserId)
                                .eq(SpecialAttention::getTargetUserId, userId)
                        );
                        
                        if (attention != null) {
                            // 发送上线/离线通知
                            if ("online".equals(newStatus) && attention.getOnlineNotification()) {
                                notificationService.sendOnlineNotification(attentionUserId, userId);
                            } else if ("offline".equals(newStatus) && attention.getOnlineNotification()) {
                                notificationService.sendOfflineNotification(attentionUserId, userId);
                            }
                            
                            // 发送状态变化通知
                            if (attention.getStatusChangeNotification()) {
                                notificationService.sendStatusChangeNotification(attentionUserId, userId, newStatus);
                            }
                        }
                    } catch (Exception e) {
                        log.error("发送状态变化通知失败: userId={}, targetUserId={}", attentionUserId, userId, e);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("处理用户状态变化通知事件失败: userId={}", event.getUserId(), e);
        }
    }
}
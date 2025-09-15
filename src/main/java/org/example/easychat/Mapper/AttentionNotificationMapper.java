package org.example.easychat.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.easychat.Entity.AttentionNotification;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AttentionNotificationMapper extends BaseMapper<AttentionNotification> {
    
    /**
     * 获取用户的通知列表
     */
    List<AttentionNotification> getNotificationsByUserId(
        @Param("userId") String userId,
        @Param("isRead") Boolean isRead,
        @Param("offset") Integer offset,
        @Param("limit") Integer limit
    );
    
    /**
     * 批量标记通知为已读
     */
    int batchMarkAsRead(@Param("userId") String userId, @Param("notificationIds") List<Long> notificationIds);
    
    /**
     * 获取未读通知数量
     */
    Long getUnreadCount(@Param("userId") String userId);
    
    /**
     * 清理过期通知
     */
    int cleanExpiredNotifications(@Param("before") LocalDateTime before);
    
    /**
     * 根据类型获取通知
     */
    List<AttentionNotification> getNotificationsByType(
        @Param("userId") String userId,
        @Param("notificationType") String notificationType,
        @Param("offset") Integer offset,
        @Param("limit") Integer limit
    );
    
    /**
     * 删除用户的所有通知
     */
    int deleteAllByUserId(@Param("userId") String userId);
}
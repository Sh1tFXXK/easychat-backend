package org.example.easychat.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 特别关心信息DTO
 */
@Data
public class AttentionInfo {
    
    /**
     * 特别关心记录ID
     */
    private Long id;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 通知设置
     */
    private NotificationSettings notificationSettings;
}
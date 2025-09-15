package org.example.easychat.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.example.easychat.dto.NotificationSettings;

import java.time.LocalDateTime;

@Data
public class AttentionCheckResult {
    private boolean hasAttention;
    private boolean mutualAttention;
    private AttentionInfo myAttention;
    private AttentionInfo theirAttention;
    
    // 兼容性方法
    public boolean getIsAttention() {
        return hasAttention;
    }
    
    public void setIsAttention(boolean isAttention) {
        this.hasAttention = isAttention;
    }
    
    public void setAttentionInfo(AttentionInfo attentionInfo) {
        this.myAttention = attentionInfo;
    }
    
    @Data
    public static class AttentionInfo {
        private Long id;
        private NotificationSettings notificationSettings;
        
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
        
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;
    }
}
package org.example.easychat.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AttentionUpdatesResult {
    private boolean hasUpdates;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastCheckTime;
    
    private List<AttentionUpdate> updates;
    
    // 兼容性方法
    public void setHasMore(boolean hasMore) {
        // 可以添加hasMore字段或者映射到hasUpdates
        this.hasUpdates = hasMore;
    }
    
    public void setLastUpdateTime(LocalDateTime lastUpdateTime) {
        this.lastCheckTime = lastUpdateTime;
    }
    
    @Data
    public static class AttentionUpdate {
        private Long id;
        private String targetUserId;
        private UpdateType updateType;
        private Object updateData;
        
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;
        
        // 兼容性方法
        public void setData(Object data) {
            this.updateData = data;
        }
        
        public void setUpdateType(String updateType) {
            try {
                this.updateType = UpdateType.valueOf(updateType.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.updateType = UpdateType.STATUS_CHANGED; // 默认值
            }
        }
        
        public enum UpdateType {
            ADDED, REMOVED, SETTINGS_CHANGED, STATUS_CHANGED
        }
    }
}
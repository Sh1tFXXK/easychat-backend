package org.example.easychat.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.example.easychat.dto.NotificationSettings;

import java.time.LocalDateTime;

@Data
public class SpecialAttentionVO {
    private Long id;
    private String userId;
    private String targetUserId;
    private TargetUserInfo targetUserInfo;
    private NotificationSettings notificationSettings;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    
    @Data
    public static class TargetUserInfo {
        private String avatar;
        private String nickName;
        private String remark;
        private String status;
        private String lastActiveTime;
    }
}

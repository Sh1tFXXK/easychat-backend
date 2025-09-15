package org.example.easychat.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.time.LocalDateTime;

@Data
@TableName("attention_notifications")
public class AttentionNotification {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @TableField("target_user_id")
    @NotBlank(message = "目标用户ID不能为空")
    private String targetUserId;
    
    @TableField("notification_type")
    @Pattern(regexp = "^(online|offline|message|status_change)$", message = "通知类型必须是online、offline、message或status_change")
    private String notificationType;
    
    private String content;
    
    @TableField("is_read")
    private Boolean isRead;
    
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
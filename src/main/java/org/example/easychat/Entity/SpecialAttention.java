package org.example.easychat.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Data
@TableName("special_attention")
public class SpecialAttention {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @TableField("target_user_id")
    @NotBlank(message = "目标用户ID不能为空")
    private String targetUserId;
    
    @TableField("online_notification")
    private Boolean onlineNotification;
    
    @TableField("offline_notification")
    private Boolean offlineNotification;
    
    @TableField("message_notification")
    private Boolean messageNotification;
    
    @TableField("status_change_notification")
    private Boolean statusChangeNotification;
    
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
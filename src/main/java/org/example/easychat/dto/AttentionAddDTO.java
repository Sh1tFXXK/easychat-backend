package org.example.easychat.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class AttentionAddDTO {
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @NotBlank(message = "目标用户ID不能为空")
    private String targetUserId;
    
    @Valid
    @NotNull(message = "通知设置不能为空")
    private NotificationSettings notificationSettings;
}
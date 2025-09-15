package org.example.easychat.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class AttentionUpdateDTO {
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @NotNull(message = "特别关心ID不能为空")
    private Long id;
    
    @Valid
    @NotNull(message = "通知设置不能为空")
    private NotificationSettings notificationSettings;
}
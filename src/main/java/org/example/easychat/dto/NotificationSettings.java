package org.example.easychat.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class NotificationSettings {
    @NotNull(message = "上线通知设置不能为空")
    private Boolean onlineNotification = true;
    
    @NotNull(message = "离线通知设置不能为空")
    private Boolean offlineNotification = true;
    
    @NotNull(message = "消息通知设置不能为空")
    private Boolean messageNotification = true;
    
    @NotNull(message = "状态变化通知设置不能为空")
    private Boolean statusChangeNotification = true;
}
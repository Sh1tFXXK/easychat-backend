package org.example.easychat.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class AttentionUpdateEvent {
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @NotBlank(message = "目标用户ID不能为空")
    private String targetUserId;
    
    @NotBlank(message = "事件类型不能为空")
    @Pattern(regexp = "^(add|remove|update)$", message = "事件类型必须是add、remove或update")
    private String eventType;
    
    private Object data; // 事件相关数据
}
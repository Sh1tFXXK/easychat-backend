package org.example.easychat.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class AttentionBatchDTO {
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @NotNull(message = "操作类型不能为空")
    private BatchOperationType operationType;
    
    @NotEmpty(message = "目标用户列表不能为空")
    @Size(max = 50, message = "批量操作数量不能超过50个")
    private List<@Valid BatchOperationItem> items;
    
    // 兼容性方法
    public BatchOperationType getOperation() {
        return operationType;
    }
    
    public List<String> getTargetUserIds() {
        if (items == null) {
            return new java.util.ArrayList<>();
        }
        return items.stream()
                .map(BatchOperationItem::getTargetUserId)
                .collect(java.util.stream.Collectors.toList());
    }
    
    public NotificationSettings getNotificationSettings() {
        // 返回第一个item的通知设置，或默认设置
        if (items != null && !items.isEmpty() && items.get(0).getNotificationSettings() != null) {
            return items.get(0).getNotificationSettings();
        }
        // 返回默认通知设置
        return new NotificationSettings();
    }
    
    public enum BatchOperationType {
        ADD, REMOVE
    }
    
    @Data
    public static class BatchOperationItem {
        @NotBlank(message = "目标用户ID不能为空")
        private String targetUserId;
        
        // 仅在ADD操作时需要
        private NotificationSettings notificationSettings;
        
        // 仅在REMOVE操作时需要（可选，如果不提供则通过targetUserId删除）
        private Long id;
    }
}
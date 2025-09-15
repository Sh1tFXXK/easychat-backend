package org.example.easychat.dto;

import lombok.Data;
import org.example.easychat.dto.validation.ValidRemoveRequest;

import javax.validation.constraints.NotBlank;

@Data
@ValidRemoveRequest
public class AttentionRemoveDTO {
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    private Long id; // 可以通过ID删除
    
    private String targetUserId; // 或者通过目标用户ID删除
    
    // 自定义验证：id和targetUserId至少有一个不为空
    public boolean isValid() {
        return id != null || (targetUserId != null && !targetUserId.trim().isEmpty());
    }
}
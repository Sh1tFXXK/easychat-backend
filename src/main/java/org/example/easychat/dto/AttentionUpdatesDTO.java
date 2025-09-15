package org.example.easychat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class AttentionUpdatesDTO {
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @NotNull(message = "最后更新时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdateTime;
    
    // 可选：指定要检查的目标用户ID列表
    private java.util.List<String> targetUserIds;
}
package org.example.easychat.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class UserStatusChangeEvent {
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(online|offline|away|busy)$", message = "状态必须是online、offline、away或busy")
    private String status;
    
    private String previousStatus;
    private Long timestamp;
}
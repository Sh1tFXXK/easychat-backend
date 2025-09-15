package org.example.easychat.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class AttentionQueryDTO {
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @Min(value = 1, message = "页码必须大于0")
    private int page = 1;
    
    @Min(value = 1, message = "每页大小必须大于0")
    @Max(value = 100, message = "每页大小不能超过100")
    private int pageSize = 20;
    
    @Pattern(regexp = "^(online|offline|away|busy|all)?$", message = "状态筛选必须是online、offline、away、busy或all")
    private String status;
}

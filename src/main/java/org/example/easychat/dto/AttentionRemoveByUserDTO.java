package org.example.easychat.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 根据目标用户ID删除特别关心的请求DTO
 */
@Data
@ApiModel(description = "根据目标用户ID删除特别关心的请求参数")
public class AttentionRemoveByUserDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @ApiModelProperty(value = "用户ID", required = true, example = "user123")
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @ApiModelProperty(value = "目标用户ID", required = true, example = "target456")
    @NotBlank(message = "目标用户ID不能为空")
    private String targetUserId;
}
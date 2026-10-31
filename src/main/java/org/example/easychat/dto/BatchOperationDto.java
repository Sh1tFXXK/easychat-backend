package org.example.easychat.dto;

import lombok.Data;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量操作DTO
 */
@Data
public class BatchOperationDto {
    
    /**
     * 收藏ID列表
     */
    @NotEmpty(message = "收藏ID列表不能为空")
    private List<Long> ids;
    
    /**
     * 目标分类ID（用于批量移动分类）
     */
    private Long categoryId;
    
    /**
     * 标签列表（用于批量标签操作）
     */
    private List<String> tags;
    
    /**
     * 要移除的标签列表
     */
    private List<String> tagsToRemove;
    
    /**
     * 标签操作类型: add, replace
     */
    private String tagOperation;
}
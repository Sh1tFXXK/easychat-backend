package org.example.easychat.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 收藏分类DTO
 */
@Data
public class FavoriteCategoryDto {
    
    /**
     * 分类名称
     */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50字符")
    private String name;
    
    /**
     * 分类图标
     */
    private String icon;
    
    /**
     * 分类颜色
     */
    private String color;
    
    /**
     * 排序顺序
     */
    private Integer sortOrder;
}
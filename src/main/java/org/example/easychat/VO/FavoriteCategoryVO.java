package org.example.easychat.VO;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 收藏分类VO
 */
@Data
public class FavoriteCategoryVO {
    
    /**
     * 分类ID
     */
    private Long id;
    
    /**
     * 分类名称
     */
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
    
    /**
     * 收藏项数量
     */
    private Integer itemCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
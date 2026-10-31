package org.example.easychat.VO;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 收藏标签VO
 */
@Data
public class FavoriteTagVO {
    
    /**
     * 标签ID
     */
    private Long id;
    
    /**
     * 标签名称
     */
    private String name;
    
    /**
     * 使用次数
     */
    private Integer useCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
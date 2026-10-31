package org.example.easychat.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 收藏标签实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("favorite_tags")
public class FavoriteTag {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 标签名称
     */
    @TableField("name")
    private String name;
    
    /**
     * 使用次数
     */
    @TableField("use_count")
    private Integer useCount;
    
    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    /**
     * 逻辑删除标志
     */
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;
}
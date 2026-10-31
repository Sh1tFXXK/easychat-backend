package org.example.easychat.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收藏搜索DTO
 */
@Data
public class FavoriteSearchDto {
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 搜索关键词
     */
    private String keyword;
    
    /**
     * 收藏类型列表
     */
    private List<String> itemTypes;
    
    /**
     * 分类ID列表
     */
    private List<Long> categoryIds;
    
    /**
     * 标签列表
     */
    private List<String> tags;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 来源聊天名称
     */
    private String sourceChatName;
    
    /**
     * 发送者名称
     */
    private String senderName;
    
    /**
     * 最小文件大小
     */
    private Long minSize;
    
    /**
     * 最大文件大小
     */
    private Long maxSize;
    
    /**
     * 可用性筛选: all, available, unavailable
     */
    private String availability;
    
    /**
     * 排序字段: time, title, type, category, size
     */
    private String sortBy;
    
    /**
     * 排序方向: asc, desc
     */
    private String sortOrder;
    
    /**
     * 页码
     */
    private Integer page;
    
    /**
     * 每页大小
     */
    private Integer pageSize;
    
    /**
     * 分类ID
     */
    private Long categoryId;
    
    /**
     * 收藏类型
     */
    private String itemType;
    
    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
    
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }
}
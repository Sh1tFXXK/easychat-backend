package org.example.easychat.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Favorite {
    private Long id;
    private String userId;
    private itemType itemType;
    private String itemId;
    private String title;
    private String content;
    private Long categoryId;
    private String tags; // JSON字符串存储标签数组
    private String notes;
    private String metadata; // JSON字符串存储元数据
    private Boolean specialCare = false;
    private String specialCarePriority; // high, medium, low
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime specialCareTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    // 关联查询字段
    private FavoriteCategory category;
}
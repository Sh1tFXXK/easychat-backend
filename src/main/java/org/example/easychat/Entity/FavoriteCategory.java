package org.example.easychat.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoriteCategory {
    private Long id;
    private String userId;
    private String name;
    private String color = "#409EFF";
    private String icon;
    private String description;
    private Integer sortOrder = 0;
    private Integer count = 0; // 该分类下的收藏数量
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
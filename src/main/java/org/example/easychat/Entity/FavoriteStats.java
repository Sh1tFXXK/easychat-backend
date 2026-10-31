package org.example.easychat.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoriteStats {
    private Long id;
    private String userId;
    private Integer totalCount = 0;
    private Integer messageCount = 0;
    private Integer userCount = 0;
    private Integer groupCount = 0;
    private Integer fileCount = 0;
    private Integer specialCareCount = 0;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
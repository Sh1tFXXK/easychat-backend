package org.example.easychat.VO;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 收藏统计VO
 */
@Data
public class FavoriteStatisticsVO {
    
    /**
     * 总收藏数
     */
    private Long totalCount;
    
    /**
     * 类型统计
     */
    private Map<String, Long> typeStatistics;
    
    /**
     * 分类统计
     */
    private List<CategoryStatistic> categoryStatistics;
    
    /**
     * 最近活动
     */
    private List<ActivityStatistic> recentActivity;
    
    @Data
    public static class CategoryStatistic {
        private Long categoryId;
        private String categoryName;
        private String categoryColor;
        private String categoryIcon;
        private Long count;
    }
    
    @Data
    public static class ActivityStatistic {
        private String date;
        private Long count;
    }
}
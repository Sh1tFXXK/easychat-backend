package org.example.easychat.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;
import org.example.easychat.Entity.Favorite;
import org.example.easychat.dto.FavoriteSearchDto;

import java.util.List;
import java.util.Map;

/**
 * 收藏 Mapper 接口
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
    
    /**
     * 分页查询收藏列表
     */
    IPage<Favorite> selectFavoriteList(Page<Favorite> page, @Param("query") FavoriteSearchDto query);
    
    /**
     * 高级搜索收藏
     */
    IPage<Favorite> advancedSearch(Page<Favorite> page, @Param("query") FavoriteSearchDto query);
    
    /**
     * 批量更新收藏分类
     */
    int batchUpdateCategory(@Param("ids") String ids, @Param("categoryId") Long categoryId, @Param("userId") Long userId);
    
    /**
     * 批量删除收藏
     */
    int batchDelete(@Param("ids") String ids, @Param("userId") Long userId);
    
    /**
     * 获取用户收藏统计
     */
    @MapKey("userId")
    List<Map<String, Object>> getTypeStatistics(  @Param("userId") Long userId);
    
    /**
     * 获取分类统计
     */
    @MapKey("userId")
    List<Map<String, Object>> getCategoryStatistics(@Param("userId") Long userId);
    
    /**
     * 获取最近活动统计
     */
    @MapKey("userId")
    List<Map<String, Object>> getRecentActivity(@Param("userId") Long userId);
    
    /**
     * 检查收藏是否已存在
     */
    int checkDuplicate(@Param("userId") Long userId, @Param("itemType") String itemType, @Param("content") String content);
    
    /**
     * 更新分类收藏数量
     */
    @Update("UPDATE favorite_categories SET item_count = (" +
            "SELECT COUNT(*) FROM favorites WHERE category_id = #{categoryId} AND deleted = 0" +
            ") WHERE id = #{categoryId}")
    void updateCategoryCount(@Param("categoryId") Long categoryId);
}
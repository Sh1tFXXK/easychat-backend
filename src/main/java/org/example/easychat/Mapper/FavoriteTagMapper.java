package org.example.easychat.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.easychat.Entity.FavoriteTag;

import java.util.List;

/**
 * 收藏标签 Mapper 接口
 */
@Mapper
public interface FavoriteTagMapper extends BaseMapper<FavoriteTag> {
    
    /**
     * 获取用户的标签列表
     */
    List<FavoriteTag> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 根据名称查找标签
     */
    FavoriteTag selectByName(@Param("userId") Long userId, @Param("name") String name);
    
    /**
     * 增加标签使用次数
     */
    void incrementUseCount(@Param("id") Long id);
    
    /**
     * 减少标签使用次数
     */
    void decrementUseCount(@Param("id") Long id);
    
    /**
     * 批量增加标签使用次数
     */
    @Update("UPDATE favorite_tags SET use_count = use_count + #{count}, update_time = NOW() WHERE id IN (${ids})")
    void batchIncrementUseCount(@Param("ids") String ids, @Param("count") Integer count);
    
    /**
     * 批量减少标签使用次数
     */
    @Update("UPDATE favorite_tags SET use_count = GREATEST(use_count - #{count}, 0), update_time = NOW() WHERE id IN (${ids})")
    void batchDecrementUseCount(@Param("ids") String ids, @Param("count") Integer count);
    
    /**
     * 清理未使用的标签
     */
    int cleanupUnusedTags(@Param("userId") Long userId);
    
    /**
     * 获取热门标签
     */
    List<FavoriteTag> getPopularTags(@Param("userId") Long userId, @Param("limit") Integer limit);
}
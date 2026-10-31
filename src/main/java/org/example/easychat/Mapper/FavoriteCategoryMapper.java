package org.example.easychat.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.easychat.Entity.FavoriteCategory;

import java.util.List;

/**
 * 收藏分类 Mapper 接口
 */
@Mapper
public interface FavoriteCategoryMapper extends BaseMapper<FavoriteCategory> {
    
    /**
     * 获取用户的分类列表
     */
    List<FavoriteCategory> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 检查分类名称是否已存在
     */
    int checkNameExists(@Param("userId") Long userId, @Param("name") String name);
    
    /**
     * 获取用户分类数量
     */
    int countByUserId(@Param("userId") Long userId);
    
    /**
     * 更新分类收藏数量
     */
    void updateItemCount(@Param("categoryId") Long categoryId);
    
    /**
     * 批量更新排序
     */
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder, @Param("userId") Long userId);
}
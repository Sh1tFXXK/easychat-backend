package org.example.easychat.Mapper;

import org.apache.ibatis.annotations.*;
import org.example.easychat.Entity.FavoriteCategory;

import java.util.List;

@Mapper
public interface FavoriteCategoryMapper {
    
    /**
     * 创建分类
     */
    @Insert("INSERT INTO favorite_categories (user_id, name, color, icon, description, sort_order, created_at, updated_at) " +
            "VALUES (#{userId}, #{name}, #{color}, #{icon}, #{description}, #{sortOrder}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCategory(FavoriteCategory category);
    
    /**
     * 检查分类名是否已存在
     */
    @Select("SELECT COUNT(*) FROM favorite_categories WHERE user_id = #{userId} AND name = #{name}")
    int checkCategoryNameExists(@Param("userId") String userId, @Param("name") String name);
    
    /**
     * 获取用户分类列表
     */
    @Select("SELECT fc.*, " +
            "(SELECT COUNT(*) FROM favorites f WHERE f.category_id = fc.id) as count " +
            "FROM favorite_categories fc " +
            "WHERE fc.user_id = #{userId} " +
            "ORDER BY fc.sort_order ASC, fc.created_at ASC")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "sortOrder", column = "sort_order"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "count", column = "count")
    })
    List<FavoriteCategory> getCategoriesByUserId(@Param("userId") String userId);
    
    /**
     * 根据ID获取分类
     */
    @Select("SELECT * FROM favorite_categories WHERE id = #{id} AND user_id = #{userId}")
    @Results({
        @Result(property = "userId", column = "user_id"),
        @Result(property = "sortOrder", column = "sort_order"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    FavoriteCategory getCategoryById(@Param("id") Long id, @Param("userId") String userId);
    
    /**
     * 更新分类
     */
    @Update("<script>" +
            "UPDATE favorite_categories SET updated_at = NOW() " +
            "<if test='name != null'>, name = #{name} </if>" +
            "<if test='color != null'>, color = #{color} </if>" +
            "<if test='icon != null'>, icon = #{icon} </if>" +
            "<if test='description != null'>, description = #{description} </if>" +
            "<if test='sortOrder != null'>, sort_order = #{sortOrder} </if>" +
            "WHERE id = #{id} AND user_id = #{userId}" +
            "</script>")
    int updateCategory(FavoriteCategory category);
    
    /**
     * 删除分类
     */
    @Delete("DELETE FROM favorite_categories WHERE id = #{id} AND user_id = #{userId}")
    int deleteCategory(@Param("id") Long id, @Param("userId") String userId);
    
    /**
     * 批量更新分类排序
     */
    @Update("<script>" +
            "<foreach collection='categories' item='category' separator=';'>" +
            "UPDATE favorite_categories SET sort_order = #{category.sortOrder}, updated_at = NOW() " +
            "WHERE id = #{category.id} AND user_id = #{userId}" +
            "</foreach>" +
            "</script>")
    int batchUpdateCategorySort(@Param("userId") String userId, @Param("categories") List<FavoriteCategory> categories);
    
    /**
     * 获取分类下的收藏数量
     */
    @Select("SELECT COUNT(*) FROM favorites WHERE category_id = #{categoryId}")
    int getFavoriteCountByCategory(@Param("categoryId") Long categoryId);
}
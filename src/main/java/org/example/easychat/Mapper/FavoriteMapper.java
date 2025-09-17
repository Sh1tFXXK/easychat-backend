package org.example.easychat.Mapper;

import org.apache.ibatis.annotations.*;
import org.example.easychat.Entity.Favorite;
import org.example.easychat.Entity.itemType;

import java.util.List;

@Mapper
public interface FavoriteMapper {
    
    /**
     * 添加收藏
     */
    @Insert("INSERT INTO favorites (user_id, item_type, item_id, title, content, category_id, tags, notes, metadata, created_at, updated_at) " +
            "VALUES (#{userId}, #{itemType}, #{itemId}, #{title}, #{content}, #{categoryId}, #{tags}, #{notes}, #{metadata}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertFavorite(Favorite favorite);
    
    /**
     * 检查收藏是否已存在
     */
    @Select("SELECT COUNT(*) FROM favorites WHERE user_id = #{userId} AND item_type = #{itemType} AND item_id = #{itemId}")
    int checkFavoriteExists(@Param("userId") String userId, @Param("itemType") itemType itemType, @Param("itemId") String itemId);
    
    /**
     * 根据ID获取收藏
     */
    @Select("SELECT f.*, fc.name as category_name, fc.color as category_color " +
            "FROM favorites f " +
            "LEFT JOIN favorite_categories fc ON f.category_id = fc.id " +
            "WHERE f.id = #{id} AND f.user_id = #{userId}")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "itemType", column = "item_type"),
        @Result(property = "itemId", column = "item_id"),
        @Result(property = "categoryId", column = "category_id"),
        @Result(property = "specialCare", column = "special_care"),
        @Result(property = "specialCarePriority", column = "special_care_priority"),
        @Result(property = "specialCareTime", column = "special_care_time"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "category.name", column = "category_name"),
        @Result(property = "category.color", column = "category_color")
    })
    Favorite getFavoriteById(@Param("id") Long id, @Param("userId") String userId);
    
    /**
     * 获取用户收藏列表
     */
    @Select("<script>" +
            "SELECT f.*, fc.name as category_name, fc.color as category_color " +
            "FROM favorites f " +
            "LEFT JOIN favorite_categories fc ON f.category_id = fc.id " +
            "WHERE f.user_id = #{userId} " +
            "<if test='categoryId != null'> AND f.category_id = #{categoryId} </if>" +
            "<if test='itemType != null'> AND f.item_type = #{itemType} </if>" +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (f.title LIKE CONCAT('%', #{keyword}, '%') OR f.content LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "<if test='specialCareOnly != null and specialCareOnly == true'> AND f.special_care = true </if>" +
            "ORDER BY " +
            "<choose>" +
            "<when test='sort == \"title\"'>f.title</when>" +
            "<when test='sort == \"updated_at\"'>f.updated_at</when>" +
            "<otherwise>f.created_at</otherwise>" +
            "</choose>" +
            "<choose>" +
            "<when test='order == \"asc\"'> ASC </when>" +
            "<otherwise> DESC </otherwise>" +
            "</choose>" +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "itemType", column = "item_type"),
        @Result(property = "itemId", column = "item_id"),
        @Result(property = "categoryId", column = "category_id"),
        @Result(property = "specialCare", column = "special_care"),
        @Result(property = "specialCarePriority", column = "special_care_priority"),
        @Result(property = "specialCareTime", column = "special_care_time"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "category.name", column = "category_name"),
        @Result(property = "category.color", column = "category_color")
    })
    List<Favorite> getFavoritesList(@Param("userId") String userId,
                                   @Param("categoryId") Long categoryId,
                                   @Param("itemType") itemType itemType,
                                   @Param("keyword") String keyword,
                                   @Param("specialCareOnly") Boolean specialCareOnly,
                                   @Param("sort") String sort,
                                   @Param("order") String order,
                                   @Param("offset") int offset,
                                   @Param("pageSize") int pageSize);
    
    /**
     * 获取收藏总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM favorites f " +
            "WHERE f.user_id = #{userId} " +
            "<if test='categoryId != null'> AND f.category_id = #{categoryId} </if>" +
            "<if test='itemType != null'> AND f.item_type = #{itemType} </if>" +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (f.title LIKE CONCAT('%', #{keyword}, '%') OR f.content LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "<if test='specialCareOnly != null and specialCareOnly == true'> AND f.special_care = true </if>" +
            "</script>")
    int getFavoritesCount(@Param("userId") String userId,
                         @Param("categoryId") Long categoryId,
                         @Param("itemType") itemType itemType,
                         @Param("keyword") String keyword,
                         @Param("specialCareOnly") Boolean specialCareOnly);
    
    /**
     * 更新收藏
     */
    @Update("<script>" +
            "UPDATE favorites SET updated_at = NOW() " +
            "<if test='title != null'>, title = #{title} </if>" +
            "<if test='content != null'>, content = #{content} </if>" +
            "<if test='categoryId != null'>, category_id = #{categoryId} </if>" +
            "<if test='tags != null'>, tags = #{tags} </if>" +
            "<if test='notes != null'>, notes = #{notes} </if>" +
            "<if test='metadata != null'>, metadata = #{metadata} </if>" +
            "WHERE id = #{id} AND user_id = #{userId}" +
            "</script>")
    int updateFavorite(Favorite favorite);
    
    /**
     * 删除收藏
     */
    @Delete("DELETE FROM favorites WHERE id = #{id} AND user_id = #{userId}")
    int deleteFavorite(@Param("id") Long id, @Param("userId") String userId);
    
    /**
     * 批量删除收藏
     */
    @Delete("<script>" +
            "DELETE FROM favorites WHERE user_id = #{userId} AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int batchDeleteFavorites(@Param("userId") String userId, @Param("ids") List<Long> ids);
    
    /**
     * 设置特别关心
     */
    @Update("UPDATE favorites SET special_care = #{specialCare}, " +
            "special_care_priority = #{priority}, " +
            "special_care_time = #{specialCare} = true ? NOW() : NULL, " +
            "updated_at = NOW() " +
            "WHERE id = #{id} AND user_id = #{userId}")
    int updateSpecialCare(@Param("id") Long id, 
                         @Param("userId") String userId, 
                         @Param("specialCare") Boolean specialCare, 
                         @Param("priority") String priority);
    
    /**
     * 获取特别关心列表
     */
    @Select("<script>" +
            "SELECT f.*, fc.name as category_name, fc.color as category_color " +
            "FROM favorites f " +
            "LEFT JOIN favorite_categories fc ON f.category_id = fc.id " +
            "WHERE f.user_id = #{userId} AND f.special_care = true " +
            "<if test='priority != null and priority != \"\"'> AND f.special_care_priority = #{priority} </if>" +
            "ORDER BY " +
            "CASE f.special_care_priority " +
            "WHEN 'high' THEN 1 " +
            "WHEN 'medium' THEN 2 " +
            "WHEN 'low' THEN 3 " +
            "ELSE 4 END, " +
            "f.special_care_time DESC " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "itemType", column = "item_type"),
        @Result(property = "itemId", column = "item_id"),
        @Result(property = "categoryId", column = "category_id"),
        @Result(property = "specialCare", column = "special_care"),
        @Result(property = "specialCarePriority", column = "special_care_priority"),
        @Result(property = "specialCareTime", column = "special_care_time"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "category.name", column = "category_name"),
        @Result(property = "category.color", column = "category_color")
    })
    List<Favorite> getSpecialCareList(@Param("userId") String userId,
                                     @Param("priority") String priority,
                                     @Param("offset") int offset,
                                     @Param("pageSize") int pageSize);
    
    /**
     * 获取特别关心总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM favorites " +
            "WHERE user_id = #{userId} AND special_care = true " +
            "<if test='priority != null and priority != \"\"'> AND special_care_priority = #{priority} </if>" +
            "</script>")
    int getSpecialCareCount(@Param("userId") String userId, @Param("priority") String priority);
}
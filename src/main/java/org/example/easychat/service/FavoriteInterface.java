package org.example.easychat.service;

import org.example.easychat.Entity.Favorite;
import org.example.easychat.Entity.FavoriteCategory;
import org.example.easychat.Entity.PageResult;
import org.example.easychat.dto.favoriteDto;
import org.example.easychat.Entity.itemType;

import java.util.List;
import java.util.Map;

public interface FavoriteInterface {
    
    /**
     * 添加收藏
     */
    Favorite addFavorite(String userId, favoriteDto favoriteDto);
    
    /**
     * 获取收藏列表
     */
    PageResult<Favorite> getFavoritesList(String userId, Long categoryId, itemType itemType, 
                                         String keyword, Boolean specialCareOnly, 
                                         String sort, String order, int page, int pageSize);
    
    /**
     * 根据ID获取收藏
     */
    Favorite getFavoriteById(Long id, String userId);
    
    /**
     * 更新收藏
     */
    boolean updateFavorite(Long id, String userId, favoriteDto favoriteDto);
    
    /**
     * 删除收藏
     */
    boolean deleteFavorite(Long id, String userId);
    
    /**
     * 批量删除收藏
     */
    boolean batchDeleteFavorites(String userId, List<Long> ids);
    
    /**
     * 设置特别关心
     */
    boolean setSpecialCare(Long id, String userId, String priority, String notes);
    
    /**
     * 取消特别关心
     */
    boolean removeSpecialCare(Long id, String userId);
    
    /**
     * 获取特别关心列表
     */
    PageResult<Favorite> getSpecialCareList(String userId, String priority, int page, int pageSize);
    
    /**
     * 获取收藏统计信息
     */
    Map<String, Object> getFavoriteStats(String userId);
    
    /**
     * 搜索收藏
     */
    PageResult<Favorite> searchFavorites(String userId, String keyword, List<itemType> itemTypes, 
                                        List<Long> categoryIds, List<String> tags, 
                                        String startDate, String endDate, 
                                        Boolean specialCareOnly, int page, int pageSize);
    
    // 分类相关方法
    
    /**
     * 创建分类
     */
    FavoriteCategory createCategory(String userId, String name, String color, String icon, String description);
    
    /**
     * 获取用户分类列表
     */
    List<FavoriteCategory> getCategoriesByUserId(String userId);
    
    /**
     * 更新分类
     */
    boolean updateCategory(Long id, String userId, String name, String color, String icon, String description);
    
    /**
     * 删除分类
     */
    boolean deleteCategory(Long id, String userId);
    
    /**
     * 分类排序
     */
    boolean sortCategories(String userId, List<FavoriteCategory> categories);
}
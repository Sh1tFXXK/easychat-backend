package org.example.easychat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.easychat.Entity.Favorite;
import org.example.easychat.Entity.FavoriteCategory;
import org.example.easychat.Entity.PageResult;
import org.example.easychat.Entity.itemType;
import org.example.easychat.Mapper.FavoriteCategoryMapper;
import org.example.easychat.Mapper.FavoriteMapper;
import org.example.easychat.dto.favoriteDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class FavoriteService implements FavoriteInterface {
    
    @Autowired
    private FavoriteMapper favoriteMapper;
    
    @Autowired
    private FavoriteCategoryMapper categoryMapper;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    @Transactional
    public Favorite addFavorite(String userId, favoriteDto favoriteDto) {
        // 检查是否已经收藏
        int exists = favoriteMapper.checkFavoriteExists(userId, favoriteDto.getItemType(), favoriteDto.getItemId());
        if (exists > 0) {
            throw new RuntimeException("该内容已经收藏过了");
        }
        
        // 创建收藏对象
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setItemType(favoriteDto.getItemType());
        favorite.setItemId(favoriteDto.getItemId());
        favorite.setTitle(favoriteDto.getTitle());
        favorite.setContent(favoriteDto.getContent());
        favorite.setCategoryId(favoriteDto.getCategoryId() > 0 ? (long) favoriteDto.getCategoryId() : null);
        favorite.setNotes(favoriteDto.getNotes());
        
        // 处理标签数组
        if (favoriteDto.getTags() != null && favoriteDto.getTags().length > 0) {
            try {
                favorite.setTags(objectMapper.writeValueAsString(favoriteDto.getTags()));
            } catch (JsonProcessingException e) {
                favorite.setTags("[]");
            }
        } else {
            favorite.setTags("[]");
        }
        
        // 处理元数据
        if (favoriteDto.getMetadata() != null) {
            try {
                favorite.setMetadata(objectMapper.writeValueAsString(favoriteDto.getMetadata()));
            } catch (JsonProcessingException e) {
                favorite.setMetadata("{}");
            }
        } else {
            favorite.setMetadata("{}");
        }
        
        // 插入数据库
        int result = favoriteMapper.insertFavorite(favorite);
        if (result > 0) {
            return favorite;
        } else {
            throw new RuntimeException("收藏失败");
        }
    }
    
    @Override
    public PageResult<Favorite> getFavoritesList(String userId, Long categoryId, itemType itemType, 
                                                String keyword, Boolean specialCareOnly, 
                                                String sort, String order, int page, int pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;
        
        // 获取总数
        int total = favoriteMapper.getFavoritesCount(userId, categoryId, itemType, keyword, specialCareOnly);
        
        // 获取列表
        List<Favorite> list = favoriteMapper.getFavoritesList(userId, categoryId, itemType, keyword, 
                                                             specialCareOnly, sort, order, offset, pageSize);
        
        // 处理标签和元数据
        for (Favorite favorite : list) {
            processJsonFields(favorite);
        }
        
        return new PageResult<>(list, total, page, pageSize);
    }
    
    @Override
    public Favorite getFavoriteById(Long id, String userId) {
        Favorite favorite = favoriteMapper.getFavoriteById(id, userId);
        if (favorite != null) {
            processJsonFields(favorite);
        }
        return favorite;
    }
    
    @Override
    @Transactional
    public boolean updateFavorite(Long id, String userId, favoriteDto favoriteDto) {
        Favorite favorite = new Favorite();
        favorite.setId(id);
        favorite.setUserId(userId);
        favorite.setTitle(favoriteDto.getTitle());
        favorite.setContent(favoriteDto.getContent());
        favorite.setCategoryId(favoriteDto.getCategoryId() > 0 ? (long) favoriteDto.getCategoryId() : null);
        favorite.setNotes(favoriteDto.getNotes());
        
        // 处理标签
        if (favoriteDto.getTags() != null) {
            try {
                favorite.setTags(objectMapper.writeValueAsString(favoriteDto.getTags()));
            } catch (JsonProcessingException e) {
                favorite.setTags("[]");
            }
        }
        
        // 处理元数据
        if (favoriteDto.getMetadata() != null) {
            try {
                favorite.setMetadata(objectMapper.writeValueAsString(favoriteDto.getMetadata()));
            } catch (JsonProcessingException e) {
                favorite.setMetadata("{}");
            }
        }
        
        return favoriteMapper.updateFavorite(favorite) > 0;
    }
    
    @Override
    @Transactional
    public boolean deleteFavorite(Long id, String userId) {
        return favoriteMapper.deleteFavorite(id, userId) > 0;
    }
    
    @Override
    @Transactional
    public boolean batchDeleteFavorites(String userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return favoriteMapper.batchDeleteFavorites(userId, ids) > 0;
    }
    
    @Override
    @Transactional
    public boolean setSpecialCare(Long id, String userId, String priority, String notes) {
        // 先更新特别关心状态
        int result = favoriteMapper.updateSpecialCare(id, userId, true, priority);
        
        // 如果有备注，更新备注
        if (result > 0 && notes != null && !notes.trim().isEmpty()) {
            Favorite favorite = new Favorite();
            favorite.setId(id);
            favorite.setUserId(userId);
            favorite.setNotes(notes);
            favoriteMapper.updateFavorite(favorite);
        }
        
        return result > 0;
    }
    
    @Override
    @Transactional
    public boolean removeSpecialCare(Long id, String userId) {
        return favoriteMapper.updateSpecialCare(id, userId, false, null) > 0;
    }
    
    @Override
    public PageResult<Favorite> getSpecialCareList(String userId, String priority, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        
        // 获取总数
        int total = favoriteMapper.getSpecialCareCount(userId, priority);
        
        // 获取列表
        List<Favorite> list = favoriteMapper.getSpecialCareList(userId, priority, offset, pageSize);
        
        // 处理JSON字段
        for (Favorite favorite : list) {
            processJsonFields(favorite);
        }
        
        return new PageResult<>(list, total, page, pageSize);
    }
    
    @Override
    public Map<String, Object> getFavoriteStats(String userId) {
        Map<String, Object> stats = new HashMap<>();
        
        // 获取总数
        int totalCount = favoriteMapper.getFavoritesCount(userId, null, null, null, null);
        stats.put("total_count", totalCount);
        
        // 获取各类型统计
        Map<String, Integer> typeStats = new HashMap<>();
        for (itemType type : itemType.values()) {
            int count = favoriteMapper.getFavoritesCount(userId, null, type, null, null);
            typeStats.put(type.name().toLowerCase(), count);
        }
        stats.put("type_stats", typeStats);
        
        // 获取特别关心数量
        int specialCareCount = favoriteMapper.getSpecialCareCount(userId, null);
        stats.put("special_care_count", specialCareCount);
        
        // 获取分类统计
        List<FavoriteCategory> categories = categoryMapper.getCategoriesByUserId(userId);
        List<Map<String, Object>> categoryStats = new ArrayList<>();
        for (FavoriteCategory category : categories) {
            Map<String, Object> categoryStat = new HashMap<>();
            categoryStat.put("category_id", category.getId());
            categoryStat.put("category_name", category.getName());
            categoryStat.put("count", category.getCount());
            categoryStats.add(categoryStat);
        }
        stats.put("category_stats", categoryStats);
        
        return stats;
    }
    
    @Override
    public PageResult<Favorite> searchFavorites(String userId, String keyword, List<itemType> itemTypes, 
                                               List<Long> categoryIds, List<String> tags, 
                                               String startDate, String endDate, 
                                               Boolean specialCareOnly, int page, int pageSize) {
        // 这里可以实现更复杂的搜索逻辑
        // 目前简化为关键词搜索
        return getFavoritesList(userId, null, null, keyword, specialCareOnly, "created_at", "desc", page, pageSize);
    }
    
    // 分类相关方法实现
    
    @Override
    @Transactional
    public FavoriteCategory createCategory(String userId, String name, String color, String icon, String description) {
        // 检查分类名是否已存在
        int exists = categoryMapper.checkCategoryNameExists(userId, name);
        if (exists > 0) {
            throw new RuntimeException("分类名称已存在");
        }
        
        FavoriteCategory category = new FavoriteCategory();
        category.setUserId(userId);
        category.setName(name);
        category.setColor(color != null ? color : "#409EFF");
        category.setIcon(icon);
        category.setDescription(description);
        category.setSortOrder(0);
        
        int result = categoryMapper.insertCategory(category);
        if (result > 0) {
            return category;
        } else {
            throw new RuntimeException("创建分类失败");
        }
    }
    
    @Override
    public List<FavoriteCategory> getCategoriesByUserId(String userId) {
        return categoryMapper.getCategoriesByUserId(userId);
    }
    
    @Override
    @Transactional
    public boolean updateCategory(Long id, String userId, String name, String color, String icon, String description) {
        FavoriteCategory category = new FavoriteCategory();
        category.setId(id);
        category.setUserId(userId);
        category.setName(name);
        category.setColor(color);
        category.setIcon(icon);
        category.setDescription(description);
        
        return categoryMapper.updateCategory(category) > 0;
    }
    
    @Override
    @Transactional
    public boolean deleteCategory(Long id, String userId) {
        // 检查分类下是否有收藏
        int count = categoryMapper.getFavoriteCountByCategory(id);
        if (count > 0) {
            throw new RuntimeException("该分类下还有收藏内容，无法删除");
        }
        
        return categoryMapper.deleteCategory(id, userId) > 0;
    }
    
    @Override
    @Transactional
    public boolean sortCategories(String userId, List<FavoriteCategory> categories) {
        return categoryMapper.batchUpdateCategorySort(userId, categories) > 0;
    }
    
    /**
     * 处理JSON字段，将字符串转换为对象
     */
    private void processJsonFields(Favorite favorite) {
        // 处理标签
        if (favorite.getTags() != null && !favorite.getTags().isEmpty()) {
            try {
                String[] tags = objectMapper.readValue(favorite.getTags(), String[].class);
                // 这里可以设置到一个临时字段或者直接在前端处理
            } catch (JsonProcessingException e) {
                // 忽略JSON解析错误
            }
        }
        
        // 处理元数据
        if (favorite.getMetadata() != null && !favorite.getMetadata().isEmpty()) {
            try {
                Map<String, Object> metadata = objectMapper.readValue(favorite.getMetadata(), Map.class);
                // 这里可以设置到一个临时字段或者直接在前端处理
            } catch (JsonProcessingException e) {
                // 忽略JSON解析错误
            }
        }
    }
}
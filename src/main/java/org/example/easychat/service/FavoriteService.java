package org.example.easychat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.easychat.Entity.Favorite;
import org.example.easychat.Entity.FavoriteCategory;
import org.example.easychat.Entity.FavoriteTag;
import org.example.easychat.Exception.BusinessException;
import org.example.easychat.Mapper.FavoriteCategoryMapper;
import org.example.easychat.Mapper.FavoriteMapper;
import org.example.easychat.Mapper.FavoriteTagMapper;
import org.example.easychat.VO.*;
import org.example.easychat.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 收藏服务实现类
 */
@Slf4j
@Service
public class FavoriteService implements FavoriteInterface, FavoriteCategoryInterface, FavoriteTagInterface {
    
    private static final Logger logger = LoggerFactory.getLogger(FavoriteService.class);
    
    @Autowired
    private FavoriteMapper favoriteMapper;
    
    @Autowired
    private FavoriteCategoryMapper categoryMapper;
    
    @Autowired
    private FavoriteTagMapper tagMapper;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // ==================== 收藏管理 ====================
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteVO addFavorite(FavoriteDto favoriteDto, Long userId) {
        logger.info("添加收藏，用户ID: {}, 类型: {}", userId, favoriteDto.getItemType());
        
        // 检查是否重复收藏：若已存在则直接返回已有记录（幂等），不抛异常
        if (checkDuplicate(favoriteDto.getItemType(), favoriteDto.getContent(), userId)) {
            QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
            wrapper.eq("user_id", userId)
                   .eq("item_type", favoriteDto.getItemType())
                   .eq("content", favoriteDto.getContent())
                   .eq("is_available", 1);
            Favorite existing = favoriteMapper.selectOne(wrapper);
            if (existing != null) {
                logger.info("重复收藏，直接返回已有记录，ID: {}", existing.getId());
                return convertToVO(existing);
            }
        }
        
        // 创建收藏实体
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setItemType(favoriteDto.getItemType());
        favorite.setTitle(generateTitle(favoriteDto));
        favorite.setContent(favoriteDto.getContent());
        favorite.setCategoryId(favoriteDto.getCategoryId());
        favorite.setNote(favoriteDto.getNote());
        favorite.setFileSize(favoriteDto.getFileSize());
        favorite.setFileType(favoriteDto.getFileType());
        favorite.setIsAvailable(true);
        
        // 处理原始数据
        if (favoriteDto.getOriginalData() != null) {
            try {
                favorite.setOriginalData(objectMapper.writeValueAsString(favoriteDto.getOriginalData()));
                favorite.setSenderId(favoriteDto.getOriginalData().getSenderId());
                favorite.setSenderName(favoriteDto.getOriginalData().getSenderName());
            } catch (JsonProcessingException e) {
                logger.error("序列化原始数据失败", e);
            }
        }
        
        // 处理来源聊天信息
        if (favoriteDto.getSourceChat() != null) {
            favorite.setSourceChatId(favoriteDto.getSourceChat().getChatId());
            favorite.setSourceChatName(favoriteDto.getSourceChat().getChatName());
            favorite.setSourceChatType(favoriteDto.getSourceChat().getChatType());
        }
        
        // 处理标签
        if (favoriteDto.getTags() != null && !favoriteDto.getTags().isEmpty()) {
            try {
                favorite.setTags(objectMapper.writeValueAsString(favoriteDto.getTags()));
                // 更新标签使用次数
                updateTagUsageCount(favoriteDto.getTags(), userId, 1);
            } catch (JsonProcessingException e) {
                logger.error("序列化标签失败", e);
            }
        }
        
        // 保存收藏
        favoriteMapper.insert(favorite);
        
        // 更新分类计数
        if (favorite.getCategoryId() != null) {
            categoryMapper.updateItemCount(favorite.getCategoryId());
        }
        
        logger.info("收藏添加成功，ID: {}", favorite.getId());
        return convertToVO(favorite);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFavorite(Long id, Long userId) {
        logger.info("删除收藏，ID: {}, 用户ID: {}", id, userId);
        
        Favorite favorite = favoriteMapper.selectById(id);
        if (favorite == null || !favorite.getUserId().equals(userId)) {
            throw new BusinessException("收藏不存在或无权限删除");
        }
        
        // 减少标签使用次数
        if (StringUtils.hasText(favorite.getTags())) {
            try {
                List<String> tags = objectMapper.readValue(favorite.getTags(), List.class);
                updateTagUsageCount(tags, userId, -1);
            } catch (JsonProcessingException e) {
                logger.error("反序列化标签失败", e);
            }
        }
        
        // 删除收藏
        favoriteMapper.deleteById(id);
        
        // 更新分类计数
        if (favorite.getCategoryId() != null) {
            categoryMapper.updateItemCount(favorite.getCategoryId());
        }
        
        logger.info("收藏删除成功");
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteFavorites(List<Long> ids, Long userId) {
        logger.info("批量删除收藏，数量: {}, 用户ID: {}", ids.size(), userId);
        
        if (ids.isEmpty()) {
            return;
        }
        
        // 获取要删除的收藏列表
        QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
        wrapper.in("id", ids).eq("user_id", userId);
        List<Favorite> favorites = favoriteMapper.selectList(wrapper);
        
        if (favorites.isEmpty()) {
            throw new BusinessException("没有找到可删除的收藏");
        }
        
        // 收集所有标签和分类
        Set<String> allTags = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        
        for (Favorite favorite : favorites) {
            if (StringUtils.hasText(favorite.getTags())) {
                try {
                    List<String> tags = objectMapper.readValue(favorite.getTags(), List.class);
                    allTags.addAll(tags);
                } catch (JsonProcessingException e) {
                    logger.error("反序列化标签失败", e);
                }
            }
            if (favorite.getCategoryId() != null) {
                categoryIds.add(favorite.getCategoryId());
            }
        }
        
        // 批量删除
        String idsStr = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        favoriteMapper.batchDelete(idsStr, userId);
        
        // 更新标签使用次数
        if (!allTags.isEmpty()) {
            updateTagUsageCount(new ArrayList<>(allTags), userId, -1);
        }
        
        // 更新分类计数
        for (Long categoryId : categoryIds) {
            categoryMapper.updateItemCount(categoryId);
        }
        
        logger.info("批量删除收藏成功");
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteVO updateFavorite(Long id, FavoriteDto favoriteDto, Long userId) {
        logger.info("更新收藏，ID: {}, 用户ID: {}", id, userId);
        
        Favorite favorite = favoriteMapper.selectById(id);
        if (favorite == null || !favorite.getUserId().equals(userId)) {
            throw new BusinessException("收藏不存在或无权限修改");
        }
        
        // 处理标签变更
        List<String> oldTags = new ArrayList<>();
        if (StringUtils.hasText(favorite.getTags())) {
            try {
                oldTags = objectMapper.readValue(favorite.getTags(), List.class);
            } catch (JsonProcessingException e) {
                logger.error("反序列化旧标签失败", e);
            }
        }
        
        Long oldCategoryId = favorite.getCategoryId();
        
        // 更新字段
        if (favoriteDto.getCategoryId() != null) {
            favorite.setCategoryId(favoriteDto.getCategoryId());
        }
        if (favoriteDto.getNote() != null) {
            favorite.setNote(favoriteDto.getNote());
        }
        if (favoriteDto.getTags() != null) {
            try {
                favorite.setTags(objectMapper.writeValueAsString(favoriteDto.getTags()));
            } catch (JsonProcessingException e) {
                logger.error("序列化新标签失败", e);
            }
        }
        
        favoriteMapper.updateById(favorite);
        
        // 更新标签使用次数
        if (!oldTags.isEmpty()) {
            updateTagUsageCount(oldTags, userId, -1);
        }
        if (favoriteDto.getTags() != null && !favoriteDto.getTags().isEmpty()) {
            updateTagUsageCount(favoriteDto.getTags(), userId, 1);
        }
        
        // 更新分类计数
        if (oldCategoryId != null && !oldCategoryId.equals(favorite.getCategoryId())) {
            categoryMapper.updateItemCount(oldCategoryId);
        }
        if (favorite.getCategoryId() != null) {
            categoryMapper.updateItemCount(favorite.getCategoryId());
        }
        
        logger.info("收藏更新成功");
        return convertToVO(favorite);
    }
    
    @Override
    public FavoriteVO getFavoriteById(Long id, Long userId) {
        Favorite favorite = favoriteMapper.selectById(id);
        if (favorite == null || !favorite.getUserId().equals(userId)) {
            throw new BusinessException("收藏不存在或无权限访问");
        }
        return convertToVO(favorite);
    }
    
    @Override
    public IPage<FavoriteVO> getFavoriteList(FavoriteSearchDto searchDto) {
        Page<Favorite> page = new Page<>(
            searchDto.getPage() != null ? searchDto.getPage() : 1,
            searchDto.getPageSize() != null ? searchDto.getPageSize() : 20
        );
        
        IPage<Favorite> result = favoriteMapper.selectFavoriteList(page, searchDto);
        
        return result.convert(this::convertToVO);
    }
    
    @Override
    public IPage<FavoriteVO> advancedSearch(FavoriteSearchDto searchDto) {
        Page<Favorite> page = new Page<>(
            searchDto.getPage() != null ? searchDto.getPage() : 1,
            searchDto.getPageSize() != null ? searchDto.getPageSize() : 20
        );
        
        IPage<Favorite> result = favoriteMapper.advancedSearch(page, searchDto);
        
        return result.convert(this::convertToVO);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateCategory(BatchOperationDto batchDto, Long userId) {
        logger.info("批量更新分类，数量: {}, 目标分类: {}", batchDto.getIds().size(), batchDto.getCategoryId());
        
        String idsStr = batchDto.getIds().stream().map(String::valueOf).collect(Collectors.joining(","));
        favoriteMapper.batchUpdateCategory(idsStr, batchDto.getCategoryId(), userId);
        
        // 更新相关分类计数
        Set<Long> categoryIds = new HashSet<>();
        categoryIds.add(batchDto.getCategoryId());
        
        // 获取原分类ID
        QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
        wrapper.in("id", batchDto.getIds()).eq("user_id", userId);
        List<Favorite> favorites = favoriteMapper.selectList(wrapper);
        
        for (Favorite favorite : favorites) {
            if (favorite.getCategoryId() != null) {
                categoryIds.add(favorite.getCategoryId());
            }
        }
        
        for (Long categoryId : categoryIds) {
            categoryMapper.updateItemCount(categoryId);
        }
        
        logger.info("批量更新分类成功");
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddTags(BatchOperationDto batchDto, Long userId) {
        logger.info("批量添加标签，收藏数量: {}, 标签: {}", batchDto.getIds().size(), batchDto.getTags());
        
        if (batchDto.getTags() == null || batchDto.getTags().isEmpty()) {
            return;
        }
        
        // 获取收藏列表
        QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
        wrapper.in("id", batchDto.getIds()).eq("user_id", userId);
        List<Favorite> favorites = favoriteMapper.selectList(wrapper);
        
        for (Favorite favorite : favorites) {
            List<String> existingTags = new ArrayList<>();
            if (StringUtils.hasText(favorite.getTags())) {
                try {
                    existingTags = objectMapper.readValue(favorite.getTags(), List.class);
                } catch (JsonProcessingException e) {
                    logger.error("反序列化标签失败", e);
                }
            }
            
            Set<String> newTags = new HashSet<>(existingTags);
            if ("replace".equals(batchDto.getTagOperation())) {
                newTags.clear();
            }
            newTags.addAll(batchDto.getTags());
            
            try {
                favorite.setTags(objectMapper.writeValueAsString(new ArrayList<>(newTags)));
                favoriteMapper.updateById(favorite);
            } catch (JsonProcessingException e) {
                logger.error("序列化标签失败", e);
            }
        }
        
        // 更新标签使用次数
        updateTagUsageCount(batchDto.getTags(), userId, batchDto.getIds().size());
        
        logger.info("批量添加标签成功");
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRemoveTags(BatchOperationDto batchDto, Long userId) {
        logger.info("批量移除标签，收藏数量: {}, 标签: {}", batchDto.getIds().size(), batchDto.getTagsToRemove());
        
        if (batchDto.getTagsToRemove() == null || batchDto.getTagsToRemove().isEmpty()) {
            return;
        }
        
        // 获取收藏列表
        QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
        wrapper.in("id", batchDto.getIds()).eq("user_id", userId);
        List<Favorite> favorites = favoriteMapper.selectList(wrapper);
        
        for (Favorite favorite : favorites) {
            if (!StringUtils.hasText(favorite.getTags())) {
                continue;
            }
            
            try {
                List<String> existingTags = objectMapper.readValue(favorite.getTags(), List.class);
                existingTags.removeAll(batchDto.getTagsToRemove());
                
                favorite.setTags(objectMapper.writeValueAsString(existingTags));
                favoriteMapper.updateById(favorite);
            } catch (JsonProcessingException e) {
                logger.error("处理标签失败", e);
            }
        }
        
        // 更新标签使用次数
        updateTagUsageCount(batchDto.getTagsToRemove(), userId, -batchDto.getIds().size());
        
        logger.info("批量移除标签成功");
    }
    
    @Override
    public FavoriteStatisticsVO getStatistics(Long userId) {
        FavoriteStatisticsVO statistics = new FavoriteStatisticsVO();
        
        // 总数统计
        QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        statistics.setTotalCount(favoriteMapper.selectCount(wrapper));
        
        // 类型统计
        List<Map<String, Object>> typeStats = favoriteMapper.getTypeStatistics(userId);
        Map<String, Long> typeStatistics = typeStats.stream()
            .collect(Collectors.toMap(
                map -> (String) map.get("item_type"),
                map -> ((Number) map.get("count")).longValue()
            ));
        statistics.setTypeStatistics(typeStatistics);
        
        // 分类统计
        List<Map<String, Object>> categoryStats = favoriteMapper.getCategoryStatistics(userId);
        List<FavoriteStatisticsVO.CategoryStatistic> categoryStatistics = categoryStats.stream()
            .map(map -> {
                FavoriteStatisticsVO.CategoryStatistic stat = new FavoriteStatisticsVO.CategoryStatistic();
                stat.setCategoryId(((Number) map.get("category_id")).longValue());
                stat.setCategoryName((String) map.get("category_name"));
                stat.setCount(((Number) map.get("count")).longValue());
                return stat;
            })
            .collect(Collectors.toList());
        statistics.setCategoryStatistics(categoryStatistics);
        
        // 最近活动统计
        List<Map<String, Object>> activityStats = favoriteMapper.getRecentActivity(userId);
        List<FavoriteStatisticsVO.ActivityStatistic> recentActivity = activityStats.stream()
            .map(map -> {
                FavoriteStatisticsVO.ActivityStatistic stat = new FavoriteStatisticsVO.ActivityStatistic();
                stat.setDate(map.get("date").toString());
                stat.setCount(((Number) map.get("count")).longValue());
                return stat;
            })
            .collect(Collectors.toList());
        statistics.setRecentActivity(recentActivity);
        
        return statistics;
    }
    
    @Override
    public boolean checkDuplicate(String itemType, String content, Long userId) {
        return favoriteMapper.checkDuplicate(userId, itemType, content) > 0;
    }
    
    // ==================== 分类管理 ====================
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteCategoryVO createCategory(FavoriteCategoryDto categoryDto, Long userId) {
        logger.info("创建收藏分类，用户ID: {}, 名称: {}", userId, categoryDto.getName());
        
        // 检查名称是否重复
        if (categoryMapper.checkNameExists(userId, categoryDto.getName()) > 0) {
            throw new BusinessException("分类名称已存在");
        }
        
        FavoriteCategory category = new FavoriteCategory();
        BeanUtils.copyProperties(categoryDto, category);
        category.setUserId(userId);
        category.setItemCount(0);
        
        if (category.getIcon() == null) {
            category.setIcon("other");
        }
        if (category.getColor() == null) {
            category.setColor("#409EFF");
        }
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        
        categoryMapper.insert(category);
        
        logger.info("分类创建成功，ID: {}", category.getId());
        return convertCategoryToVO(category);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteCategoryVO updateCategory(Long id, FavoriteCategoryDto categoryDto, Long userId) {
        logger.info("更新收藏分类，ID: {}, 用户ID: {}", id, userId);
        
        FavoriteCategory category = categoryMapper.selectById(id);
        if (category == null || !category.getUserId().equals(userId)) {
            throw new BusinessException("分类不存在或无权限修改");
        }
        
        // 检查名称是否重复（排除自己）
        QueryWrapper<FavoriteCategory> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
               .eq("name", categoryDto.getName())
               .ne("id", id);
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("分类名称已存在");
        }
        
        BeanUtils.copyProperties(categoryDto, category, "id", "userId", "itemCount", "createTime");
        categoryMapper.updateById(category);
        
        logger.info("分类更新成功");
        return convertCategoryToVO(category);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id, Long moveToCategory, Long userId) {
        logger.info("删除收藏分类，ID: {}, 移动到: {}, 用户ID: {}", id, moveToCategory, userId);
        
        FavoriteCategory category = categoryMapper.selectById(id);
        if (category == null || !category.getUserId().equals(userId)) {
            throw new BusinessException("分类不存在或无权限删除");
        }
        
        // 处理该分类下的收藏
        QueryWrapper<Favorite> favoriteWrapper = new QueryWrapper<>();
        favoriteWrapper.eq("category_id", id).eq("user_id", userId);
        List<Favorite> favorites = favoriteMapper.selectList(favoriteWrapper);
        
        if (!favorites.isEmpty()) {
            if (moveToCategory != null) {
                // 移动到指定分类
                for (Favorite favorite : favorites) {
                    favorite.setCategoryId(moveToCategory);
                    favoriteMapper.updateById(favorite);
                }
                // 更新目标分类计数
                categoryMapper.updateItemCount(moveToCategory);
            } else {
                // 设置为无分类
                for (Favorite favorite : favorites) {
                    favorite.setCategoryId(null);
                    favoriteMapper.updateById(favorite);
                }
            }
        }
        
        // 删除分类
        categoryMapper.deleteById(id);
        
        logger.info("分类删除成功");
    }
    
    @Override
    public List<FavoriteCategoryVO> getCategoriesByUserId(Long userId) {
        List<FavoriteCategory> categories = categoryMapper.selectByUserId(userId);
        return categories.stream()
                .map(this::convertCategoryToVO)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initDefaultCategories(Long userId) {
        logger.info("初始化默认分类，用户ID: {}", userId);
        
        // 检查是否已有分类
        if (categoryMapper.countByUserId(userId) > 0) {
            return;
        }
        
        // 创建默认分类
        List<FavoriteCategory> defaultCategories = Arrays.asList(
            createDefaultCategory(userId, "工作", "work", "#409EFF", 1),
            createDefaultCategory(userId, "生活", "life", "#67C23A", 2),
            createDefaultCategory(userId, "重要", "important", "#E6A23C", 3),
            createDefaultCategory(userId, "其他", "other", "#909399", 4)
        );
        
        for (FavoriteCategory category : defaultCategories) {
            categoryMapper.insert(category);
        }
        
        logger.info("默认分类初始化完成");
    }
    
    // ==================== 标签管理 ====================
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteTagVO createTag(String name, Long userId) {
        logger.info("创建收藏标签，用户ID: {}, 名称: {}", userId, name);
        
        // 检查是否已存在
        FavoriteTag existingTag = tagMapper.selectByName(userId, name);
        if (existingTag != null) {
            return convertTagToVO(existingTag);
        }
        
        FavoriteTag tag = new FavoriteTag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setUseCount(0);
        
        tagMapper.insert(tag);
        
        logger.info("标签创建成功，ID: {}", tag.getId());
        return convertTagToVO(tag);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id, Long userId) {
        logger.info("删除收藏标签，ID: {}, 用户ID: {}", id, userId);
        
        FavoriteTag tag = tagMapper.selectById(id);
        if (tag == null || !tag.getUserId().equals(userId)) {
            throw new BusinessException("标签不存在或无权限删除");
        }
        
        // TODO: 从所有收藏中移除该标签
        
        tagMapper.deleteById(id);
        
        logger.info("标签删除成功");
    }
    
    @Override
    public List<FavoriteTagVO> getTagsByUserId(Long userId) {
        List<FavoriteTag> tags = tagMapper.selectByUserId(userId);
        return tags.stream()
                .map(this::convertTagToVO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<FavoriteTagVO> getPopularTags(Long userId, Integer limit) {
        List<FavoriteTag> tags = tagMapper.getPopularTags(userId, limit != null ? limit : 10);
        return tags.stream()
                .map(this::convertTagToVO)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanupUnusedTags(Long userId) {
        logger.info("清理未使用的标签，用户ID: {}", userId);
        int count = tagMapper.cleanupUnusedTags(userId);
        logger.info("清理了 {} 个未使用的标签", count);
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 生成收藏标题
     */
    private String generateTitle(FavoriteDto favoriteDto) {
        if (StringUtils.hasText(favoriteDto.getTitle())) {
            return favoriteDto.getTitle();
        }
        
        switch (favoriteDto.getItemType()) {
            case "message":
                String content = favoriteDto.getContent();
                return content != null && content.length() > 20 
                    ? content.substring(0, 20) + "..." 
                    : content;
            case "image":
                return "[图片]";
            case "file":
                return favoriteDto.getOriginalData() != null && favoriteDto.getOriginalData().getFileName() != null
                    ? favoriteDto.getOriginalData().getFileName()
                    : "[文件]";
            case "voice":
                return "[语音] " + (favoriteDto.getOriginalData() != null 
                    ? favoriteDto.getOriginalData().getDuration() + "秒" 
                    : "");
            case "video":
                return "[视频]";
            case "contact":
                return "[联系人]";
            default:
                return "[未知类型]";
        }
    }
    
    /**
     * 更新标签使用次数
     */
    private void updateTagUsageCount(List<String> tags, Long userId, int delta) {
        for (String tagName : tags) {
            FavoriteTag tag = tagMapper.selectByName(userId, tagName);
            if (tag == null) {
                // 创建新标签
                tag = new FavoriteTag();
                tag.setUserId(userId);
                tag.setName(tagName);
                tag.setUseCount(Math.max(0, delta));
                tagMapper.insert(tag);
            } else {
                // 更新使用次数
                if (delta > 0) {
                    tagMapper.incrementUseCount(tag.getId());
                } else {
                    tagMapper.decrementUseCount(tag.getId());
                }
            }
        }
    }
    
    /**
     * 创建默认分类
     */
    private FavoriteCategory createDefaultCategory(Long userId, String name, String icon, String color, int sortOrder) {
        FavoriteCategory category = new FavoriteCategory();
        category.setUserId(userId);
        category.setName(name);
        category.setIcon(icon);
        category.setColor(color);
        category.setSortOrder(sortOrder);
        category.setItemCount(0);
        return category;
    }
    
    /**
     * 转换为VO
     */
    private FavoriteVO convertToVO(Favorite favorite) {
        FavoriteVO vo = new FavoriteVO();
        BeanUtils.copyProperties(favorite, vo);
        
        // 处理标签
        if (StringUtils.hasText(favorite.getTags())) {
            try {
                List<String> tags = objectMapper.readValue(favorite.getTags(), List.class);
                vo.setTags(tags);
            } catch (JsonProcessingException e) {
                logger.error("反序列化标签失败", e);
                vo.setTags(new ArrayList<>());
            }
        } else {
            vo.setTags(new ArrayList<>());
        }
        
        // 获取分类信息
        if (favorite.getCategoryId() != null) {
            FavoriteCategory category = categoryMapper.selectById(favorite.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
                vo.setCategoryColor(category.getColor());
                vo.setCategoryIcon(category.getIcon());
            }
        }
        
        return vo;
    }
    
    /**
     * 转换分类为VO
     */
    private FavoriteCategoryVO convertCategoryToVO(FavoriteCategory category) {
        FavoriteCategoryVO vo = new FavoriteCategoryVO();
        BeanUtils.copyProperties(category, vo);
        return vo;
    }
    
    /**
     * 转换标签为VO
     */
    private FavoriteTagVO convertTagToVO(FavoriteTag tag) {
        FavoriteTagVO vo = new FavoriteTagVO();
        BeanUtils.copyProperties(tag, vo);
        return vo;
    }
}

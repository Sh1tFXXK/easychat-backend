package org.example.easychat.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.easychat.VO.*;
import org.example.easychat.dto.*;

import java.util.List;

/**
 * 收藏服务接口
 */
public interface FavoriteInterface {
    
    /**
     * 添加收藏
     */
    FavoriteVO addFavorite(FavoriteDto favoriteDto, Long userId);
    
    /**
     * 删除收藏
     */
    void deleteFavorite(Long id, Long userId);
    
    /**
     * 批量删除收藏
     */
    void batchDeleteFavorites(List<Long> ids, Long userId);
    
    /**
     * 更新收藏
     */
    FavoriteVO updateFavorite(Long id, FavoriteDto favoriteDto, Long userId);
    
    /**
     * 获取收藏详情
     */
    FavoriteVO getFavoriteById(Long id, Long userId);
    
    /**
     * 获取收藏列表
     */
    IPage<FavoriteVO> getFavoriteList(FavoriteSearchDto searchDto);
    
    /**
     * 高级搜索收藏
     */
    IPage<FavoriteVO> advancedSearch(FavoriteSearchDto searchDto);
    
    /**
     * 批量更新收藏分类
     */
    void batchUpdateCategory(BatchOperationDto batchDto, Long userId);
    
    /**
     * 批量添加标签
     */
    void batchAddTags(BatchOperationDto batchDto, Long userId);
    
    /**
     * 批量移除标签
     */
    void batchRemoveTags(BatchOperationDto batchDto, Long userId);
    
    /**
     * 获取收藏统计
     */
    FavoriteStatisticsVO getStatistics(Long userId);
    
    /**
     * 检查收藏是否重复
     */
    boolean checkDuplicate(String itemType, String content, Long userId);
}


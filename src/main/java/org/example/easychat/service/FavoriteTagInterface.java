package org.example.easychat.service;

import org.example.easychat.VO.FavoriteTagVO;

import java.util.List;

/**
 * 收藏标签服务接口
 */
interface FavoriteTagInterface {
    
    /**
     * 创建标签
     */
    FavoriteTagVO createTag(String name, Long userId);
    
    /**
     * 删除标签
     */
    void deleteTag(Long id, Long userId);
    
    /**
     * 获取用户标签列表
     */
    List<FavoriteTagVO> getTagsByUserId(Long userId);
    
    /**
     * 获取热门标签
     */
    List<FavoriteTagVO> getPopularTags(Long userId, Integer limit);
    
    /**
     * 清理未使用的标签
     */
    void cleanupUnusedTags(Long userId);
}
package org.example.easychat.service;

import org.example.easychat.VO.FavoriteCategoryVO;
import org.example.easychat.dto.FavoriteCategoryDto;

import java.util.List;

/**
 * 收藏分类服务接口
 */
interface FavoriteCategoryInterface {

    /**
     * 创建分类
     */
    FavoriteCategoryVO createCategory(FavoriteCategoryDto categoryDto, Long userId);

    /**
     * 更新分类
     */
    FavoriteCategoryVO updateCategory(Long id, FavoriteCategoryDto categoryDto, Long userId);

    /**
     * 删除分类
     */
    void deleteCategory(Long id, Long moveToCategory, Long userId);

    /**
     * 获取用户分类列表
     */
    List<FavoriteCategoryVO> getCategoriesByUserId(Long userId);

    /**
     * 初始化默认分类
     */
    void initDefaultCategories(Long userId);
}

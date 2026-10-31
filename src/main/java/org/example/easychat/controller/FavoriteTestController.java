package org.example.easychat.controller;

import org.example.easychat.BO.ResponseBO;
import org.example.easychat.Entity.Favorite;
import org.example.easychat.Entity.FavoriteCategory;
import org.example.easychat.Entity.itemType;
import org.example.easychat.dto.favoriteDto;
import org.example.easychat.service.FavoriteInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏功能测试控制器
 * 用于测试收藏功能的各个接口
 */
@RestController
@RequestMapping("/api/test")
public class FavoriteTestController {
    
    @Autowired
    private FavoriteInterface favoriteService;
    
    /**
     * 测试添加收藏
     */
    @PostMapping("/favorite/add")
    public ResponseBO<Map<String, Object>> testAddFavorite() {
        try {
            // 创建测试数据
            favoriteDto dto = new favoriteDto();
            dto.setItemType(itemType.MESSAGE);
            dto.setItemId("test_message_001");
            dto.setTitle("测试消息收藏");
            dto.setContent("这是一条测试消息的内容");
            dto.setTags(new String[]{"测试", "重要"});
            dto.setNotes("这是测试备注");
            
            // 使用测试用户ID
            String testUserId = "test_user_001";
            
            Favorite favorite = favoriteService.addFavorite(testUserId, dto);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", favorite.getId());
            result.put("title", favorite.getTitle());
            result.put("created_at", favorite.getCreatedAt());
            
            return ResponseBO.success("测试添加收藏成功", result);
        } catch (Exception e) {
            return ResponseBO.error("测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试创建分类
     */
    @PostMapping("/category/add")
    public ResponseBO<Map<String, Object>> testCreateCategory() {
        try {
            String testUserId = "test_user_001";
            
            FavoriteCategory category = favoriteService.createCategory(
                testUserId, 
                "测试分类", 
                "#67C23A", 
                "test-icon", 
                "这是一个测试分类"
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", category.getId());
            result.put("name", category.getName());
            result.put("color", category.getColor());
            
            return ResponseBO.success("测试创建分类成功", result);
        } catch (Exception e) {
            return ResponseBO.error("测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试获取收藏列表
     */
    @GetMapping("/favorites/list")
    public ResponseBO<?> testGetFavoritesList() {
        try {
            String testUserId = "test_user_001";
            
            var result = favoriteService.getFavoritesList(
                testUserId, null, null, null, null, 
                "created_at", "desc", 1, 10
            );
            
            return ResponseBO.success("测试获取收藏列表成功", result);
        } catch (Exception e) {
            return ResponseBO.error("测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试获取分类列表
     */
    @GetMapping("/categories/list")
    public ResponseBO<List<FavoriteCategory>> testGetCategoriesList() {
        try {
            String testUserId = "test_user_001";
            
            List<FavoriteCategory> categories = favoriteService.getCategoriesByUserId(testUserId);
            
            return ResponseBO.success("测试获取分类列表成功", categories);
        } catch (Exception e) {
            return ResponseBO.error("测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试设置特别关心
     */
    @PostMapping("/favorite/{id}/special-care")
    public ResponseBO<String> testSetSpecialCare(@PathVariable Long id) {
        try {
            String testUserId = "test_user_001";
            
            boolean success = favoriteService.setSpecialCare(id, testUserId, "high", "测试特别关心备注");
            
            if (success) {
                return ResponseBO.success("测试设置特别关心成功");
            } else {
                return ResponseBO.error("测试设置特别关心失败");
            }
        } catch (Exception e) {
            return ResponseBO.error("测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试获取特别关心列表
     */
    @GetMapping("/special-care/list")
    public ResponseBO<?> testGetSpecialCareList() {
        try {
            String testUserId = "test_user_001";
            
            var result = favoriteService.getSpecialCareList(testUserId, null, 1, 10);
            
            return ResponseBO.success("测试获取特别关心列表成功", result);
        } catch (Exception e) {
            return ResponseBO.error("测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试获取统计信息
     */
    @GetMapping("/stats")
    public ResponseBO<Map<String, Object>> testGetStats() {
        try {
            String testUserId = "test_user_001";
            
            Map<String, Object> stats = favoriteService.getFavoriteStats(testUserId);
            
            return ResponseBO.success("测试获取统计信息成功", stats);
        } catch (Exception e) {
            return ResponseBO.error("测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量创建测试数据
     */
    @PostMapping("/create-test-data")
    public ResponseBO<Map<String, Object>> createTestData() {
        try {
            String testUserId = "test_user_001";
            int successCount = 0;
            int failedCount = 0;
            
            // 创建测试分类
            try {
                favoriteService.createCategory(testUserId, "工作", "#409EFF", "briefcase", "工作相关收藏");
                favoriteService.createCategory(testUserId, "学习", "#67C23A", "book", "学习资料收藏");
                favoriteService.createCategory(testUserId, "生活", "#E6A23C", "home", "生活相关收藏");
                successCount += 3;
            } catch (Exception e) {
                failedCount += 3;
            }
            
            // 创建测试收藏
            itemType[] types = {itemType.MESSAGE, itemType.USER, itemType.GROUP, itemType.FILE};
            String[] titles = {"重要会议记录", "技术文档", "项目资料", "学习笔记", "生活照片"};
            
            for (int i = 0; i < 5; i++) {
                try {
                    favoriteDto dto = new favoriteDto();
                    dto.setItemType(types[i % types.length]);
                    dto.setItemId("test_item_" + (i + 1));
                    dto.setTitle(titles[i]);
                    dto.setContent("这是测试内容 " + (i + 1));
                    dto.setTags(new String[]{"测试", "示例"});
                    dto.setNotes("测试备注 " + (i + 1));
                    
                    favoriteService.addFavorite(testUserId, dto);
                    successCount++;
                } catch (Exception e) {
                    failedCount++;
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success_count", successCount);
            result.put("failed_count", failedCount);
            result.put("message", "测试数据创建完成");
            
            return ResponseBO.success("批量创建测试数据完成", result);
        } catch (Exception e) {
            return ResponseBO.error("创建测试数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理测试数据
     */
    @DeleteMapping("/cleanup-test-data")
    public ResponseBO<String> cleanupTestData() {
        try {
            String testUserId = "test_user_001";
            
            // 获取测试用户的所有收藏
            var favorites = favoriteService.getFavoritesList(
                testUserId, null, null, null, null, 
                "created_at", "desc", 1, 100
            );
            
            // 删除所有收藏
            for (Favorite favorite : favorites.getList()) {
                favoriteService.deleteFavorite(favorite.getId(), testUserId);
            }
            
            // 删除所有分类
            List<FavoriteCategory> categories = favoriteService.getCategoriesByUserId(testUserId);
            for (FavoriteCategory category : categories) {
                try {
                    favoriteService.deleteCategory(category.getId(), testUserId);
                } catch (Exception e) {
                    // 忽略删除分类时的错误（可能有外键约束）
                }
            }
            
            return ResponseBO.success("测试数据清理完成");
        } catch (Exception e) {
            return ResponseBO.error("清理测试数据失败: " + e.getMessage());
        }
    }
}
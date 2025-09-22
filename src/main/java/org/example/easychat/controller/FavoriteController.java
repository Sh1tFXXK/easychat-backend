package org.example.easychat.controller;

import org.example.easychat.BO.ResponseBO;
import org.example.easychat.Entity.Favorite;
import org.example.easychat.Entity.FavoriteCategory;
import org.example.easychat.Entity.PageResult;
import org.example.easychat.Entity.itemType;
import org.example.easychat.dto.favoriteDto;
import org.example.easychat.service.FavoriteInterface;
import org.example.easychat.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FavoriteController {
    
    @Autowired
    private FavoriteInterface favoriteService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * 添加收藏
     */
    @PostMapping("/favorites")
    public ResponseBO<Map<String, Object>> addFavorite(@RequestBody favoriteDto favoriteDto, HttpServletRequest request) {
        try {
            String userId = getUserIdFromToken(request);
            Favorite favorite = favoriteService.addFavorite(userId, favoriteDto);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", favorite.getId());
            result.put("created_at", favorite.getCreatedAt());
            
            return ResponseBO.success("收藏成功", result);
        } catch (Exception e) {
            return ResponseBO.error("收藏失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取收藏列表
     */
    @GetMapping("/favorites")
    public ResponseBO<PageResult<Favorite>> getFavoritesList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size,
            @RequestParam(required = false) Long category_id,
            @RequestParam(required = false) itemType item_type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean special_care_only,
            @RequestParam(defaultValue = "created_at") String sort,
            @RequestParam(defaultValue = "desc") String order,
            HttpServletRequest request) {
        try {
            String userId = getUserIdFromToken(request);
            PageResult<Favorite> result = favoriteService.getFavoritesList(
                userId, category_id, item_type, keyword, special_care_only, sort, order, page, page_size
            );
            return ResponseBO.success("获取成功", result);
        } catch (Exception e) {
            return ResponseBO.error("获取失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取收藏
     */
    @GetMapping("/favorites/{id}")
    public ResponseBO<Favorite> getFavoriteById(@PathVariable Long id, HttpServletRequest request) {
        try {
            String userId = getUserIdFromToken(request);
            Favorite favorite = favoriteService.getFavoriteById(id, userId);
            if (favorite != null) {
                return ResponseBO.success("获取成功", favorite);
            } else {
                return ResponseBO.error("收藏不存在");
            }
        } catch (Exception e) {
            return ResponseBO.error("获取失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新收藏
     */
    @PutMapping("/favorites/{id}")
    public ResponseBO<String> updateFavorite(@PathVariable Long id, @RequestBody favoriteDto favoriteDto, HttpServletRequest request) {
        try {
            String userId = getUserIdFromToken(request);
            boolean success = favoriteService.updateFavorite(id, userId, favoriteDto);
            if (success) {
                return ResponseBO.success("更新成功");
            } else {
                return ResponseBO.error("更新失败");
            }
        } catch (Exception e) {
            return ResponseBO.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除收藏
     */
    @DeleteMapping("/favorites/{id}")
    public ResponseBO<String> deleteFavorite(@PathVariable Long id, HttpServletRequest request) {
        try {
            String userId = getUserIdFromToken(request);
            boolean success = favoriteService.deleteFavorite(id, userId);
            if (success) {
                return ResponseBO.success("删除成功");
            } else {
                return ResponseBO.error("删除失败");
            }
        } catch (Exception e) {
            return ResponseBO.error("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量操作收藏
     */
    @PostMapping("/favorites/batch")
    public ResponseBO<Map<String, Object>> batchOperateFavorites(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        try {
            String userId = getUserIdFromToken(httpRequest);
            String action = (String) request.get("action");
            List<Long> ids = (List<Long>) request.get("ids");
            
            Map<String, Object> result = new HashMap<>();
            
            if ("delete".equals(action)) {
                boolean success = favoriteService.batchDeleteFavorites(userId, ids);
                result.put("success_count", success ? ids.size() : 0);
                result.put("failed_count", success ? 0 : ids.size());
                return ResponseBO.success("批量删除成功", result);
            }
            
            return ResponseBO.error("不支持的操作类型");
        } catch (Exception e) {
            return ResponseBO.error("批量操作失败: " + e.getMessage());
        }
    }
    
    /**
     * 设置特别关心
     */
    @PostMapping("/favorites/{id}/special-care")
    public ResponseBO<String> setSpecialCare(@PathVariable Long id, @RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        try {
            String userId = getUserIdFromToken(httpRequest);
            String priority = request.get("priority");
            String notes = request.get("notes");
            
            boolean success = favoriteService.setSpecialCare(id, userId, priority, notes);
            if (success) {
                return ResponseBO.success("设置特别关心成功");
            } else {
                return ResponseBO.error("设置失败");
            }
        } catch (Exception e) {
            return ResponseBO.error("设置失败: " + e.getMessage());
        }
    }
    
    /**
     * 取消特别关心
     */
    @DeleteMapping("/favorites/{id}/special-care")
    public ResponseBO<String> removeSpecialCare(@PathVariable Long id, HttpServletRequest request) {
        try {
            String userId = getUserIdFromToken(request);
            boolean success = favoriteService.removeSpecialCare(id, userId);
            if (success) {
                return ResponseBO.success("取消特别关心成功");
            } else {
                return ResponseBO.error("取消失败");
            }
        } catch (Exception e) {
            return ResponseBO.error("取消失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取特别关心列表
     */
    @GetMapping("/special-care")
    public ResponseBO<PageResult<Favorite>> getSpecialCareList(
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size,
            HttpServletRequest request) {
        try {
            String userId = getUserIdFromToken(request);
            PageResult<Favorite> result = favoriteService.getSpecialCareList(userId, priority, page, page_size);
            return ResponseBO.success("获取成功", result);
        } catch (Exception e) {
            return ResponseBO.error("获取失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新特别关心
     */
    @PutMapping("/special-care/{id}")
    public ResponseBO<String> updateSpecialCare(@PathVariable Long id, @RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        try {
            String userId = getUserIdFromToken(httpRequest);
            String priority = request.get("priority");
            String notes = request.get("notes");
            
            boolean success = favoriteService.setSpecialCare(id, userId, priority, notes);
            if (success) {
                return ResponseBO.success("更新成功");
            } else {
                return ResponseBO.error("更新失败");
            }
        } catch (Exception e) {
            return ResponseBO.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取收藏统计
     */
    @GetMapping("/favorites/stats")
    public ResponseBO<Map<String, Object>> getFavoriteStats(HttpServletRequest request) {
        try {
            String userId = getUserIdFromToken(request);
            Map<String, Object> stats = favoriteService.getFavoriteStats(userId);
            return ResponseBO.success("获取成功", stats);
        } catch (Exception e) {
            return ResponseBO.error("获取失败: " + e.getMessage());
        }
    }
    
    /**
     * 搜索收藏
     */
    @GetMapping("/favorites/search")
    public ResponseBO<PageResult<Favorite>> searchFavorites(
            @RequestParam String q,
            @RequestParam(required = false) itemType type,
            @RequestParam(required = false) Long category_id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size,
            HttpServletRequest request) {
        try {
            String userId = getUserIdFromToken(request);
            PageResult<Favorite> result = favoriteService.searchFavorites(
                userId, q, null, null, null, null, null, null, page, page_size
            );
            return ResponseBO.success("搜索成功", result);
        } catch (Exception e) {
            return ResponseBO.error("搜索失败: " + e.getMessage());
        }
    }
    
    // 分类相关接口
    
    /**
     * 获取分类列表
     */
    @GetMapping("/favorite-categories")
    public ResponseBO<List<FavoriteCategory>> getCategoriesList(HttpServletRequest request) {
        try {
            String userId = getUserIdFromToken(request);
            List<FavoriteCategory> categories = favoriteService.getCategoriesByUserId(userId);
            return ResponseBO.success("获取成功", categories);
        } catch (Exception e) {
            return ResponseBO.error("获取失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建分类
     */
    @PostMapping("/favorite-categories")
    public ResponseBO<Map<String, Object>> createCategory(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        try {
            String userId = getUserIdFromToken(httpRequest);
            String name = request.get("name");
            String color = request.get("color");
            String icon = request.get("icon");
            String description = request.get("description");
            
            FavoriteCategory category = favoriteService.createCategory(userId, name, color, icon, description);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", category.getId());
            
            return ResponseBO.success("创建成功", result);
        } catch (Exception e) {
            return ResponseBO.error("创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新分类
     */
    @PutMapping("/favorite-categories/{id}")
    public ResponseBO<String> updateCategory(@PathVariable Long id, @RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        try {
            String userId = getUserIdFromToken(httpRequest);
            String name = request.get("name");
            String color = request.get("color");
            String icon = request.get("icon");
            String description = request.get("description");
            
            boolean success = favoriteService.updateCategory(id, userId, name, color, icon, description);
            if (success) {
                return ResponseBO.success("更新成功");
            } else {
                return ResponseBO.error("更新失败");
            }
        } catch (Exception e) {
            return ResponseBO.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除分类
     */
    @DeleteMapping("/favorite-categories/{id}")
    public ResponseBO<String> deleteCategory(@PathVariable Long id, HttpServletRequest request) {
        try {
            String userId = getUserIdFromToken(request);
            boolean success = favoriteService.deleteCategory(id, userId);
            if (success) {
                return ResponseBO.success("删除成功");
            } else {
                return ResponseBO.error("删除失败");
            }
        } catch (Exception e) {
            return ResponseBO.error("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 分类排序
     */
    @PostMapping("/favorite-categories/sort")
    public ResponseBO<String> sortCategories(@RequestBody Map<String, List<FavoriteCategory>> request, HttpServletRequest httpRequest) {
        try {
            String userId = getUserIdFromToken(httpRequest);
            List<FavoriteCategory> orders = request.get("orders");
            
            boolean success = favoriteService.sortCategories(userId, orders);
            if (success) {
                return ResponseBO.success("排序成功");
            } else {
                return ResponseBO.error("排序失败");
            }
        } catch (Exception e) {
            return ResponseBO.error("排序失败: " + e.getMessage());
        }
    }
    
    /**
     * 从请求中获取用户ID
     */
    private String getUserIdFromToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        throw new RuntimeException("未找到有效的认证信息");
    }
}
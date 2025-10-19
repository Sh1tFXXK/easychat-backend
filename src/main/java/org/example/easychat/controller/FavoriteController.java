package org.example.easychat.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.easychat.BO.ApiResponseBO;
import org.example.easychat.VO.*;
import org.example.easychat.dto.*;
import org.example.easychat.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.example.easychat.utils.JwtUtil;
import org.example.easychat.Entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

/**
 * 收藏功能控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/favorites")
@Tag(name = "收藏管理", description = "收藏功能相关接口")
public class FavoriteController {
    
    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private JwtUtil jwtUtil;
    
    // ==================== 收藏管理 ====================
    
    @PostMapping
    @Operation(summary = "添加收藏", description = "将消息、文件等内容添加到收藏")
    public ResponseEntity<ApiResponseBO<FavoriteVO>> addFavorite(
            @Valid @RequestBody FavoriteDto favoriteDto,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 添加收藏，类型: {}", userId, favoriteDto.getItemType());
        
        FavoriteVO result = favoriteService.addFavorite(favoriteDto, userId);
        return ResponseEntity.ok(ApiResponseBO.success(result, "收藏成功"));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除收藏", description = "删除指定的收藏项")
    public ResponseEntity<ApiResponseBO<Void>> deleteFavorite(
            @Parameter(description = "收藏ID") @PathVariable Long id,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 删除收藏: {}", userId, id);
        
        favoriteService.deleteFavorite(id, userId);
        return ResponseEntity.ok(ApiResponseBO.success(null, "删除成功"));
    }
    
    @DeleteMapping("/batch")
    @Operation(summary = "批量删除收藏", description = "批量删除多个收藏项")
    public ResponseEntity<ApiResponseBO<Void>> batchDeleteFavorites(
            @RequestBody List<Long> ids,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 批量删除收藏，数量: {}", userId, ids.size());
        
        favoriteService.batchDeleteFavorites(ids, userId);
        return ResponseEntity.ok(ApiResponseBO.success(null, "批量删除成功"));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "更新收藏", description = "更新收藏的分类、标签、备注等信息")
    public ResponseEntity<ApiResponseBO<FavoriteVO>> updateFavorite(
            @Parameter(description = "收藏ID") @PathVariable Long id,
            @Valid @RequestBody FavoriteDto favoriteDto,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 更新收藏: {}", userId, id);
        
        FavoriteVO result = favoriteService.updateFavorite(id, favoriteDto, userId);
        return ResponseEntity.ok(ApiResponseBO.success(result, "更新成功"));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "获取收藏详情", description = "获取指定收藏项的详细信息")
    public ResponseEntity<ApiResponseBO<FavoriteVO>> getFavoriteById(
            @Parameter(description = "收藏ID") @PathVariable Long id,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        FavoriteVO result = favoriteService.getFavoriteById(id, userId);
        return ResponseEntity.ok(ApiResponseBO.success(result));
    }
    
    @GetMapping
    @Operation(summary = "获取收藏列表", description = "分页获取用户的收藏列表")
    public ResponseEntity<ApiResponseBO<IPage<FavoriteVO>>> getFavoriteList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "内容类型") @RequestParam(required = false) String itemType,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "标签") @RequestParam(required = false) List<String> tags,
            @Parameter(description = "排序字段") @RequestParam(defaultValue = "createTime") String sortBy,
            @Parameter(description = "排序方向") @RequestParam(defaultValue = "desc") String sortOrder,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        
        FavoriteSearchDto searchDto = new FavoriteSearchDto();
        searchDto.setUserId(userId);
        searchDto.setPage(page);
        searchDto.setPageSize(pageSize);
        searchDto.setCategoryId(categoryId);
        searchDto.setItemType(itemType);
        searchDto.setKeyword(keyword);
        searchDto.setTags(tags);
        searchDto.setSortBy(sortBy);
        searchDto.setSortOrder(sortOrder);
        
        IPage<FavoriteVO> result = favoriteService.getFavoriteList(searchDto);
        return ResponseEntity.ok(ApiResponseBO.success(result));
    }
    
    @PostMapping("/search")
    @Operation(summary = "高级搜索", description = "使用复杂条件搜索收藏")
    public ResponseEntity<ApiResponseBO<IPage<FavoriteVO>>> advancedSearch(
            @Valid @RequestBody FavoriteSearchDto searchDto,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        searchDto.setUserId(userId);
        
        IPage<FavoriteVO> result = favoriteService.advancedSearch(searchDto);
        return ResponseEntity.ok(ApiResponseBO.success(result));
    }
    
    @PutMapping("/batch/category")
    @Operation(summary = "批量更新分类", description = "批量修改收藏项的分类")
    public ResponseEntity<ApiResponseBO<Void>> batchUpdateCategory(
            @Valid @RequestBody BatchOperationDto batchDto,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 批量更新分类，数量: {}", userId, batchDto.getIds().size());
        
        favoriteService.batchUpdateCategory(batchDto, userId);
        return ResponseEntity.ok(ApiResponseBO.success(null, "批量更新分类成功"));
    }
    
    @PutMapping("/batch/tags/add")
    @Operation(summary = "批量添加标签", description = "为多个收藏项批量添加标签")
    public ResponseEntity<ApiResponseBO<Void>> batchAddTags(
            @Valid @RequestBody BatchOperationDto batchDto,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 批量添加标签，数量: {}", userId, batchDto.getIds().size());
        
        favoriteService.batchAddTags(batchDto, userId);
        return ResponseEntity.ok(ApiResponseBO.success(null, "批量添加标签成功"));
    }
    
    @PutMapping("/batch/tags/remove")
    @Operation(summary = "批量移除标签", description = "为多个收藏项批量移除标签")
    public ResponseEntity<ApiResponseBO<Void>> batchRemoveTags(
            @Valid @RequestBody BatchOperationDto batchDto,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 批量移除标签，数量: {}", userId, batchDto.getIds().size());
        
        favoriteService.batchRemoveTags(batchDto, userId);
        return ResponseEntity.ok(ApiResponseBO.success(null, "批量移除标签成功"));
    }
    
    @GetMapping("/statistics")
    @Operation(summary = "获取收藏统计", description = "获取用户收藏的统计信息")
    public ResponseEntity<ApiResponseBO<FavoriteStatisticsVO>> getStatistics(
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        FavoriteStatisticsVO result = favoriteService.getStatistics(userId);
        return ResponseEntity.ok(ApiResponseBO.success(result));
    }
    
    @GetMapping("/check-duplicate")
    @Operation(summary = "检查重复收藏", description = "检查内容是否已经收藏过")
    public ResponseEntity<ApiResponseBO<Boolean>> checkDuplicate(
            @Parameter(description = "内容类型") @RequestParam String itemType,
            @Parameter(description = "内容") @RequestParam String content,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        boolean isDuplicate = favoriteService.checkDuplicate(itemType, content, userId);
        return ResponseEntity.ok(ApiResponseBO.success(isDuplicate));
    }
    
    // ==================== 分类管理 ====================
    
    @PostMapping("/categories")
    @Operation(summary = "创建收藏分类", description = "创建新的收藏分类")
    public ResponseEntity<ApiResponseBO<FavoriteCategoryVO>> createCategory(
            @Valid @RequestBody FavoriteCategoryDto categoryDto,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 创建分类: {}", userId, categoryDto.getName());
        
        FavoriteCategoryVO result = favoriteService.createCategory(categoryDto, userId);
        return ResponseEntity.ok(ApiResponseBO.success(result, "分类创建成功"));
    }
    
    @PutMapping("/categories/{id}")
    @Operation(summary = "更新收藏分类", description = "更新收藏分类信息")
    public ResponseEntity<ApiResponseBO<FavoriteCategoryVO>> updateCategory(
            @Parameter(description = "分类ID") @PathVariable Long id,
            @Valid @RequestBody FavoriteCategoryDto categoryDto,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 更新分类: {}", userId, id);
        
        FavoriteCategoryVO result = favoriteService.updateCategory(id, categoryDto, userId);
        return ResponseEntity.ok(ApiResponseBO.success(result, "分类更新成功"));
    }
    
    @DeleteMapping("/categories/{id}")
    @Operation(summary = "删除收藏分类", description = "删除收藏分类，可选择将分类下的收藏移动到其他分类")
    public ResponseEntity<ApiResponseBO<Void>> deleteCategory(
            @Parameter(description = "分类ID") @PathVariable Long id,
            @Parameter(description = "移动到的分类ID") @RequestParam(required = false) Long moveToCategory,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 删除分类: {}, 移动到: {}", userId, id, moveToCategory);
        
        favoriteService.deleteCategory(id, moveToCategory, userId);
        return ResponseEntity.ok(ApiResponseBO.success(null, "分类删除成功"));
    }
    
    @GetMapping("/categories")
    @Operation(summary = "获取收藏分类列表", description = "获取用户的所有收藏分类")
    public ResponseEntity<ApiResponseBO<List<FavoriteCategoryVO>>> getCategories(
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        List<FavoriteCategoryVO> result = favoriteService.getCategoriesByUserId(userId);
        return ResponseEntity.ok(ApiResponseBO.success(result));
    }
    
    @PostMapping("/categories/init")
    @Operation(summary = "初始化默认分类", description = "为新用户初始化默认的收藏分类")
    public ResponseEntity<ApiResponseBO<Void>> initDefaultCategories(
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 初始化默认分类", userId);
        
        favoriteService.initDefaultCategories(userId);
        return ResponseEntity.ok(ApiResponseBO.success(null, "默认分类初始化成功"));
    }
    
    // ==================== 标签管理 ====================
    
    @PostMapping("/tags")
    @Operation(summary = "创建收藏标签", description = "创建新的收藏标签")
    public ResponseEntity<ApiResponseBO<FavoriteTagVO>> createTag(
            @Parameter(description = "标签名称") @RequestParam String name,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 创建标签: {}", userId, name);
        
        FavoriteTagVO result = favoriteService.createTag(name, userId);
        return ResponseEntity.ok(ApiResponseBO.success(result, "标签创建成功"));
    }
    
    @DeleteMapping("/tags/{id}")
    @Operation(summary = "删除收藏标签", description = "删除收藏标签")
    public ResponseEntity<ApiResponseBO<Void>> deleteTag(
            @Parameter(description = "标签ID") @PathVariable Long id,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 删除标签: {}", userId, id);
        
        favoriteService.deleteTag(id, userId);
        return ResponseEntity.ok(ApiResponseBO.success(null, "标签删除成功"));
    }
    
    @GetMapping("/tags")
    @Operation(summary = "获取收藏标签列表", description = "获取用户的所有收藏标签")
    public ResponseEntity<ApiResponseBO<List<FavoriteTagVO>>> getTags(
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        List<FavoriteTagVO> result = favoriteService.getTagsByUserId(userId);
        return ResponseEntity.ok(ApiResponseBO.success(result));
    }
    
    @GetMapping("/tags/popular")
    @Operation(summary = "获取热门标签", description = "获取使用频率最高的标签")
    public ResponseEntity<ApiResponseBO<List<FavoriteTagVO>>> getPopularTags(
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        List<FavoriteTagVO> result = favoriteService.getPopularTags(userId, limit);
        return ResponseEntity.ok(ApiResponseBO.success(result));
    }
    
    @DeleteMapping("/tags/cleanup")
    @Operation(summary = "清理未使用标签", description = "清理使用次数为0的标签")
    public ResponseEntity<ApiResponseBO<Void>> cleanupUnusedTags(
            HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("用户 {} 清理未使用标签", userId);
        
        favoriteService.cleanupUnusedTags(userId);
        return ResponseEntity.ok(ApiResponseBO.success(null, "清理完成"));
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 从请求中获取用户ID
     * 这里需要根据实际的认证机制来实现
     */
    private Long getUserIdFromRequest(HttpServletRequest request) {
        // 1) 优先从 Spring Security 上下文获取
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User) {
                String uid = ((User) authentication.getPrincipal()).getId();
                if (uid != null && uid.matches("\\d+")) {
                    return Long.parseLong(uid);
                }
            }
        } catch (Exception e) {
            log.warn("从SecurityContext获取用户失败: {}", e.getMessage());
        }

        // 2) 尝试从 Authorization: Bearer <token> 解析
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            String token = bearer.substring(7);
            try {
                String uid = jwtUtil.getUserIdFromToken(token);
                if (uid != null && uid.matches("\\d+")) {
                    return Long.parseLong(uid);
                }
            } catch (Exception e) {
                log.warn("JWT解析失败(Authorization): {}", e.getMessage());
            }
        }

        // 3) 尝试从 Cookie 中的 token 解析
        javax.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (javax.servlet.http.Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    String tokenVal = cookie.getValue();
                    if (tokenVal != null) {
                        if (tokenVal.startsWith("Bearer ")) {
                            tokenVal = tokenVal.substring(7);
                        }
                        try {
                            String uid = jwtUtil.getUserIdFromToken(tokenVal);
                            if (uid != null && uid.matches("\\d+")) {
                                return Long.parseLong(uid);
                            }
                        } catch (Exception e) {
                            log.warn("JWT解析失败(Cookie): {}", e.getMessage());
                        }
                    }
                }
            }
        }

        // 4) 兜底：X-User-Id 请求头（不推荐，仅兼容）
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null) {
            try {
                return Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                log.error("无效的用户ID格式: {}", userIdHeader);
            }
        }

        throw new RuntimeException("未找到用户信息，请先登录");
    }
}

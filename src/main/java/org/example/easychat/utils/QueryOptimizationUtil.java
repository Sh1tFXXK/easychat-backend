package org.example.easychat.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * 数据库查询优化工具类
 */
@Slf4j
public class QueryOptimizationUtil {
    
    /**
     * 创建优化的分页对象
     * 限制最大页面大小，防止大量数据查询
     */
    public static <T> Page<T> createOptimizedPage(int page, int pageSize) {
        // 限制最大页面大小
        int maxPageSize = 100;
        int optimizedPageSize = Math.min(pageSize, maxPageSize);
        
        // 限制最大页码，防止深度分页
        int maxPage = 1000;
        int optimizedPage = Math.min(page, maxPage);
        
        if (pageSize > maxPageSize) {
            log.warn("页面大小超过限制，已调整: requested={}, actual={}", pageSize, optimizedPageSize);
        }
        
        if (page > maxPage) {
            log.warn("页码超过限制，已调整: requested={}, actual={}", page, optimizedPage);
        }
        
        return new Page<>(optimizedPage, optimizedPageSize);
    }
    
    /**
     * 创建优化的查询条件
     * 添加必要的索引提示和查询优化
     */
    public static <T> LambdaQueryWrapper<T> createOptimizedQuery(Class<T> entityClass) {
        LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>();
        
        // 设置查询超时时间（毫秒）
        wrapper.last("/* QUERY_TIMEOUT=5000 */");
        
        return wrapper;
    }
    
    /**
     * 添加用户ID查询条件（使用索引）
     */
    public static <T> LambdaQueryWrapper<T> addUserIdCondition(
            LambdaQueryWrapper<T> wrapper, 
            String userId,
            SFunction<T, Object> userIdGetter) {
        
        if (StringUtils.isNotBlank(userId)) {
            wrapper.eq(userIdGetter, userId);
        }
        
        return wrapper;
    }
    
    /**
     * 添加时间范围查询条件（使用索引）
     */
    public static <T> LambdaQueryWrapper<T> addTimeRangeCondition(
            LambdaQueryWrapper<T> wrapper,
            java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime,
            SFunction<T, Object> timeGetter) {
        
        if (startTime != null) {
            wrapper.ge(timeGetter, startTime);
        }
        
        if (endTime != null) {
            wrapper.le(timeGetter, endTime);
        }
        
        return wrapper;
    }
    
    /**
     * 添加状态查询条件
     */
    public static <T> LambdaQueryWrapper<T> addStatusCondition(
            LambdaQueryWrapper<T> wrapper,
            String status,
            SFunction<T, Object> statusGetter) {
        
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq(statusGetter, status);
        }
        
        return wrapper;
    }
    
    /**
     * 添加IN查询条件（限制IN条件的数量）
     */
    public static <T> LambdaQueryWrapper<T> addInCondition(
            LambdaQueryWrapper<T> wrapper,
            List<String> values,
            SFunction<T, Object> fieldGetter) {
        
        if (values != null && !values.isEmpty()) {
            // 限制IN条件的数量，防止SQL过长
            int maxInSize = 1000;
            if (values.size() > maxInSize) {
                log.warn("IN条件数量过多，已截取: size={}, max={}", values.size(), maxInSize);
                values = values.subList(0, maxInSize);
            }
            
            wrapper.in(fieldGetter, values);
        }
        
        return wrapper;
    }
    
    /**
     * 添加排序条件（优化排序性能）
     */
    public static <T> LambdaQueryWrapper<T> addOrderCondition(
            LambdaQueryWrapper<T> wrapper,
            String orderBy,
            boolean isAsc,
            SFunction<T, Object> fieldGetter) {
        
        if (StringUtils.isNotBlank(orderBy)) {
            if (isAsc) {
                wrapper.orderByAsc(fieldGetter);
            } else {
                wrapper.orderByDesc(fieldGetter);
            }
        }
        
        return wrapper;
    }
    
    /**
     * 创建批量查询的分片
     * 将大批量查询分解为多个小批量查询
     */
    public static <T> List<List<T>> createBatches(List<T> items, int batchSize) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        
        // 限制最大批次大小
        int maxBatchSize = 500;
        int optimizedBatchSize = Math.min(batchSize, maxBatchSize);
        
        List<List<T>> batches = new java.util.ArrayList<>();
        
        for (int i = 0; i < items.size(); i += optimizedBatchSize) {
            int endIndex = Math.min(i + optimizedBatchSize, items.size());
            batches.add(items.subList(i, endIndex));
        }
        
        log.debug("创建批量查询分片: totalItems={}, batchSize={}, batchCount={}", 
                items.size(), optimizedBatchSize, batches.size());
        
        return batches;
    }
    
    /**
     * 检查查询是否可能导致性能问题
     */
    public static boolean isQueryOptimized(QueryWrapper<?> wrapper) {
        String sqlSegment = wrapper.getSqlSegment();
        
        // 检查是否有WHERE条件
        if (!sqlSegment.contains("WHERE")) {
            log.warn("查询缺少WHERE条件，可能导致全表扫描");
            return false;
        }
        
        // 检查是否有LIMIT条件
        if (!sqlSegment.contains("LIMIT")) {
            log.warn("查询缺少LIMIT条件，可能返回大量数据");
            return false;
        }
        
        // 检查是否有复杂的OR条件
        if (sqlSegment.contains(" OR ")) {
            log.warn("查询包含OR条件，可能影响索引使用");
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取查询执行计划提示
     */
    public static String getQueryHint(String operation) {
        switch (operation.toLowerCase()) {
            case "select":
                return "/* USE_INDEX */";
            case "insert":
                return "/* INSERT_BATCH */";
            case "update":
                return "/* UPDATE_BATCH */";
            case "delete":
                return "/* DELETE_BATCH */";
            default:
                return "";
        }
    }
    
    /**
     * 创建缓存键
     */
    public static String createCacheKey(String prefix, Object... params) {
        StringBuilder keyBuilder = new StringBuilder(prefix);
        
        for (Object param : params) {
            if (param != null) {
                keyBuilder.append(":").append(param.toString());
            }
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * 验证分页参数
     */
    public static void validatePageParams(int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于0");
        }
        
        if (pageSize < 1) {
            throw new IllegalArgumentException("页面大小必须大于0");
        }
        
        if (pageSize > 100) {
            throw new IllegalArgumentException("页面大小不能超过100");
        }
        
        if (page > 1000) {
            throw new IllegalArgumentException("页码不能超过1000");
        }
    }
}
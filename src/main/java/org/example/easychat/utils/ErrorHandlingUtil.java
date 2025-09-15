package org.example.easychat.utils;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.Handler.BusinessException;
import org.example.easychat.Handler.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * 错误处理工具类
 * 提供统一的错误处理和异常转换方法
 */
@Slf4j
public class ErrorHandlingUtil {

    /**
     * 安全执行操作，捕获并转换异常
     */
    public static <T> T safeExecute(Supplier<T> operation, String operationName) {
        try {
            return operation.get();
        } catch (Exception e) {
            throw convertException(e, operationName);
        }
    }

    /**
     * 安全执行操作，提供默认值
     */
    public static <T> T safeExecuteWithDefault(Supplier<T> operation, T defaultValue, String operationName) {
        try {
            return operation.get();
        } catch (Exception e) {
            log.warn("操作执行失败，返回默认值 - 操作: {}, 错误: {}", operationName, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * 安全执行操作，忽略异常
     */
    public static void safeExecuteIgnoreException(Runnable operation, String operationName) {
        try {
            operation.run();
        } catch (Exception e) {
            log.warn("操作执行失败，已忽略异常 - 操作: {}, 错误: {}", operationName, e.getMessage());
        }
    }

    /**
     * 转换异常为业务异常
     */
    public static BusinessException convertException(Exception e, String operationName) {
        log.error("操作执行失败 - 操作: {}, 异常类型: {}, 错误信息: {}", 
            operationName, e.getClass().getSimpleName(), e.getMessage(), e);

        // 如果已经是业务异常，直接返回
        if (e instanceof BusinessException) {
            return (BusinessException) e;
        }

        // 处理异步任务异常
        if (e instanceof CompletionException && e.getCause() instanceof BusinessException) {
            return (BusinessException) e.getCause();
        }

        // 处理数据库相关异常
        if (e instanceof DataAccessException) {
            return convertDataAccessException((DataAccessException) e, operationName);
        }

        // 处理SQL异常
        if (e instanceof SQLException) {
            return convertSQLException((SQLException) e, operationName);
        }

        // 处理参数异常
        if (e instanceof IllegalArgumentException) {
            return BusinessException.paramError(e.getMessage());
        }

        // 处理状态异常
        if (e instanceof IllegalStateException) {
            return BusinessException.systemError("系统状态异常: " + e.getMessage(), e);
        }

        // 处理空指针异常
        if (e instanceof NullPointerException) {
            return BusinessException.systemError("空指针异常: " + operationName, e);
        }

        // 默认转换为系统异常
        return BusinessException.systemError("操作失败: " + operationName + " - " + e.getMessage(), e);
    }

    /**
     * 转换数据访问异常
     */
    private static BusinessException convertDataAccessException(DataAccessException e, String operationName) {
        if (e instanceof DuplicateKeyException) {
            return new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR, "数据重复，操作失败", e);
        }
        
        if (e instanceof DataIntegrityViolationException) {
            return new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR, "数据完整性约束违反", e);
        }
        
        return new BusinessException(ErrorCode.DATABASE_ERROR, "数据库操作失败: " + operationName, e);
    }

    /**
     * 转换SQL异常
     */
    private static BusinessException convertSQLException(SQLException e, String operationName) {
        String sqlState = e.getSQLState();
        int errorCode = e.getErrorCode();
        
        // 根据SQL状态码和错误码进行具体分类
        if ("23000".equals(sqlState)) {
            // 完整性约束违反
            return new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR, "数据完整性约束违反", e);
        }
        
        if ("42000".equals(sqlState)) {
            // 语法错误或访问违规
            return new BusinessException(ErrorCode.DATABASE_ERROR, "SQL语法错误", e);
        }
        
        if ("08000".equals(sqlState) || "08001".equals(sqlState) || "08004".equals(sqlState)) {
            // 连接异常
            return new BusinessException(ErrorCode.DATABASE_ERROR, "数据库连接异常", e);
        }
        
        return new BusinessException(ErrorCode.DATABASE_ERROR, 
            String.format("SQL异常 - 操作: %s, 错误码: %d, 状态: %s", operationName, errorCode, sqlState), e);
    }

    /**
     * 验证参数并抛出异常
     */
    public static void validateParam(boolean condition, String message) {
        if (!condition) {
            throw BusinessException.paramError(message);
        }
    }

    /**
     * 验证参数不为空
     */
    public static void validateNotNull(Object param, String paramName) {
        if (param == null) {
            throw BusinessException.paramError(paramName + " 不能为空");
        }
    }

    /**
     * 验证字符串参数不为空
     */
    public static void validateNotBlank(String param, String paramName) {
        if (param == null || param.trim().isEmpty()) {
            throw BusinessException.paramError(paramName + " 不能为空");
        }
    }

    /**
     * 验证数字参数范围
     */
    public static void validateRange(Number param, Number min, Number max, String paramName) {
        if (param == null) {
            throw BusinessException.paramError(paramName + " 不能为空");
        }
        
        double value = param.doubleValue();
        double minValue = min.doubleValue();
        double maxValue = max.doubleValue();
        
        if (value < minValue || value > maxValue) {
            throw BusinessException.paramError(
                String.format("%s 必须在 %s 到 %s 之间", paramName, min, max));
        }
    }

    /**
     * 验证集合参数不为空
     */
    public static void validateNotEmpty(java.util.Collection<?> collection, String paramName) {
        if (collection == null || collection.isEmpty()) {
            throw BusinessException.paramError(paramName + " 不能为空");
        }
    }

    /**
     * 验证集合大小
     */
    public static void validateCollectionSize(java.util.Collection<?> collection, int maxSize, String paramName) {
        if (collection != null && collection.size() > maxSize) {
            throw BusinessException.paramError(
                String.format("%s 大小不能超过 %d", paramName, maxSize));
        }
    }

    /**
     * 验证业务条件
     */
    public static void validateBusinessCondition(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new BusinessException(errorCode);
        }
    }

    public static void validateBusinessCondition(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new BusinessException(errorCode, message);
        }
    }

    /**
     * 记录并抛出业务异常
     */
    public static void throwBusinessException(ErrorCode errorCode, String message, Object... args) {
        String formattedMessage = String.format(message, args);
        log.error("业务异常 - 错误码: {}, 消息: {}", errorCode.getCode(), formattedMessage);
        throw new BusinessException(errorCode, formattedMessage);
    }

    /**
     * 记录并抛出系统异常
     */
    public static void throwSystemException(String message, Throwable cause, Object... args) {
        String formattedMessage = String.format(message, args);
        log.error("系统异常 - 消息: {}", formattedMessage, cause);
        throw BusinessException.systemError(formattedMessage, cause);
    }

    /**
     * 特别关心功能专用验证方法
     */
    public static class AttentionValidation {
        
        /**
         * 验证用户ID
         */
        public static void validateUserId(String userId) {
            validateNotBlank(userId, "用户ID");
            if (userId.length() > 19) {
                throw BusinessException.paramError("用户ID长度不能超过19位");
            }
        }

        /**
         * 验证不能关心自己
         */
        public static void validateNotSelf(String userId, String targetUserId) {
            if (userId.equals(targetUserId)) {
                throw new BusinessException(ErrorCode.CANNOT_ATTENTION_SELF);
            }
        }

        /**
         * 验证特别关心数量限制
         */
        public static void validateAttentionLimit(int currentCount, int maxLimit) {
            if (currentCount >= maxLimit) {
                throw new BusinessException(ErrorCode.ATTENTION_LIMIT_EXCEEDED, 
                    String.format("特别关心数量不能超过 %d 个", maxLimit));
            }
        }

        /**
         * 验证批量操作大小
         */
        public static void validateBatchSize(int batchSize, int maxSize) {
            if (batchSize > maxSize) {
                throw new BusinessException(ErrorCode.BATCH_SIZE_EXCEEDED, 
                    String.format("批量操作数量不能超过 %d 个", maxSize));
            }
        }
    }
}
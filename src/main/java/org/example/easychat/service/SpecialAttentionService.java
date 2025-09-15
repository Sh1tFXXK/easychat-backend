package org.example.easychat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.example.easychat.Entity.*;
import org.example.easychat.Mapper.SpecialAttentionMapper;
import org.example.easychat.Mapper.UserMapper;
import org.example.easychat.VO.AttentionCheckResult;
import org.example.easychat.VO.AttentionUpdatesResult;
import org.example.easychat.VO.BatchOperationResult;
import org.example.easychat.VO.SpecialAttentionVO;
import org.example.easychat.dto.*;
import org.example.easychat.Handler.BusinessException;
import org.example.easychat.Handler.ErrorCode;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
public class SpecialAttentionService implements SpecialAttentionInterface {

    @Autowired
    private SpecialAttentionMapper attentionMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserStatusService userStatusService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private org.example.easychat.event.AttentionEventPublisher eventPublisher;
    
    @Autowired(required = false)
    private CacheService cacheService;
    
    @Autowired(required = false)
    private RateLimitService rateLimitService;
    
    @Autowired(required = false)
    private PerformanceMonitorService performanceMonitorService;

    private static final String ATTENTION_CACHE_KEY = "attention:user:";
    private static final int MAX_ATTENTION_COUNT = 100; // 最大特别关心数量限制



    @Override
    public PageResult<SpecialAttentionVO> getAttentionList(AttentionQueryDTO queryDTO) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 限流检查（如果服务可用）
            if (rateLimitService != null) {
                rateLimitService.checkQueryAttentionRateLimit(queryDTO.getUserId());
            }
            
            // 参数验证
            validateQueryParams(queryDTO);

            // 计算分页参数
            int offset = (queryDTO.getPage() - 1) * queryDTO.getPageSize();

            // 查询数据
            List<SpecialAttentionVO> list = attentionMapper.getAttentionListWithUserInfo(
                    queryDTO.getUserId(),
                    queryDTO.getStatus(),
                    offset,
                    queryDTO.getPageSize()
            );

            // 获取总数
            long total = attentionMapper.selectCount(
                    new LambdaQueryWrapper<org.example.easychat.Entity.SpecialAttention>()
                            .eq(org.example.easychat.Entity.SpecialAttention::getUserId, queryDTO.getUserId())
            );

            // 记录性能指标（如果服务可用）
            if (performanceMonitorService != null) {
                long responseTime = System.currentTimeMillis() - startTime;
                performanceMonitorService.recordResponseTime("getAttentionList", responseTime);
                performanceMonitorService.recordRequestCount("getAttentionList");
            }

            log.info("获取特别关心列表成功: userId={}, total={}", queryDTO.getUserId(), total);
            return new PageResult<>(list, total, queryDTO.getPage(), queryDTO.getPageSize());

        } catch (BusinessException e) {
            if (performanceMonitorService != null) {
                performanceMonitorService.recordErrorCount("getAttentionList", e.getClass().getSimpleName());
            }
            throw e;
        } catch (Exception e) {
            if (performanceMonitorService != null) {
                performanceMonitorService.recordErrorCount("getAttentionList", "SystemError");
            }
            log.error("获取特别关心列表失败: userId={}", queryDTO.getUserId(), e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "获取特别关心列表失败");
        }
    }

    /**
     * 添加特别关心
     */
    @Override
    public SpecialAttentionVO addAttention(AttentionAddDTO addDTO) {
        log.info("开始添加特别关心: userId={}, targetUserId={}", addDTO.getUserId(), addDTO.getTargetUserId());
        
        // 参数验证
        validateAddParams(addDTO);

        try {
            // 检查是否已存在
            log.info("检查特别关心关系是否已存在: userId={}, targetUserId={}", addDTO.getUserId(), addDTO.getTargetUserId());
            if (attentionMapper.existsAttention(addDTO.getUserId(), addDTO.getTargetUserId())) {
                log.warn("特别关心关系已存在: userId={}, targetUserId={}", addDTO.getUserId(), addDTO.getTargetUserId());
                throw new BusinessException(ErrorCode.ATTENTION_ALREADY_EXISTS);
            }

            // 检查特别关心数量限制
            long currentCount = attentionMapper.selectCount(
                    new LambdaQueryWrapper<org.example.easychat.Entity.SpecialAttention>()
                            .eq(org.example.easychat.Entity.SpecialAttention::getUserId, addDTO.getUserId())
            );
            log.info("当前特别关心数量: userId={}, count={}", addDTO.getUserId(), currentCount);
            if (currentCount >= MAX_ATTENTION_COUNT) {
                log.warn("特别关心数量已达上限: userId={}, count={}", addDTO.getUserId(), currentCount);
                throw new BusinessException(ErrorCode.ATTENTION_LIMIT_EXCEEDED);
            }

            // 创建特别关心记录
            org.example.easychat.Entity.SpecialAttention attention = new org.example.easychat.Entity.SpecialAttention();
            BeanUtils.copyProperties(addDTO, attention);

            // 设置通知设置
            NotificationSettings settings = addDTO.getNotificationSettings();
            if (settings != null) {
                attention.setOnlineNotification(settings.getOnlineNotification());
                attention.setOfflineNotification(settings.getOfflineNotification());
                attention.setMessageNotification(settings.getMessageNotification());
                attention.setStatusChangeNotification(settings.getStatusChangeNotification());
            } else {
                // 默认设置
                attention.setOnlineNotification(true);
                attention.setOfflineNotification(false);
                attention.setMessageNotification(true);
                attention.setStatusChangeNotification(false);
            }

            attention.setCreateTime(LocalDateTime.now());
            attention.setUpdateTime(LocalDateTime.now());

            // 插入数据库
            log.info("插入特别关心记录到数据库: userId={}, targetUserId={}", addDTO.getUserId(), addDTO.getTargetUserId());
            attentionMapper.insert(attention);

            // 清除缓存
            clearAttentionCache(addDTO.getUserId());

            // 发送Socket.IO事件 - 使用事件发布器避免循环依赖
            eventPublisher.publishAttentionUpdateEvent(addDTO.getUserId(), addDTO.getTargetUserId(), "add");

            log.info("添加特别关心成功: userId={}, targetUserId={}", addDTO.getUserId(), addDTO.getTargetUserId());
            return buildAttentionVO(attention);

        } catch (BusinessException e) {
            log.error("添加特别关心业务异常: userId={}, targetUserId={}, error={}", 
                     addDTO.getUserId(), addDTO.getTargetUserId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("添加特别关心系统异常: userId={}, targetUserId={}", 
                     addDTO.getUserId(), addDTO.getTargetUserId(), e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "添加特别关心失败");
        }
    }

    /**
     * 删除特别关心
     */
    @Override
    public void removeAttention(AttentionRemoveDTO removeDTO) {
        // 验证参数
        validateRemoveParams(removeDTO);

        try {
            // 删除记录
            LambdaQueryWrapper<org.example.easychat.Entity.SpecialAttention> wrapper = new LambdaQueryWrapper<>();
            if (removeDTO.getId() != null) {
                wrapper.eq(org.example.easychat.Entity.SpecialAttention::getId, removeDTO.getId());
            } else {
                wrapper.eq(org.example.easychat.Entity.SpecialAttention::getUserId, removeDTO.getUserId())
                        .eq(org.example.easychat.Entity.SpecialAttention::getTargetUserId, removeDTO.getTargetUserId());
            }
            wrapper.eq(org.example.easychat.Entity.SpecialAttention::getUserId, removeDTO.getUserId()); // 确保权限

            int deleted = attentionMapper.delete(wrapper);
            if (deleted == 0) {
                throw new BusinessException(ErrorCode.ATTENTION_NOT_FOUND);
            }

            // 清除缓存
            clearAttentionCache(removeDTO.getUserId());

            // 发送Socket.IO事件 - 使用事件发布器避免循环依赖
            eventPublisher.publishAttentionUpdateEvent(
                    removeDTO.getUserId(),
                    removeDTO.getTargetUserId(),
                    "remove"
            );

            log.info("删除特别关心成功: userId={}, targetUserId={}", removeDTO.getUserId(), removeDTO.getTargetUserId());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除特别关心失败: userId={}, targetUserId={}", removeDTO.getUserId(), removeDTO.getTargetUserId(), e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "删除特别关心失败");
        }
    }

    /**
     * 更新通知设置
     */
    @Override
    public void updateNotificationSettings(AttentionUpdateDTO updateDTO) {
        // 验证参数
        validateUpdateParams(updateDTO);

        try {
            // 更新记录
            org.example.easychat.Entity.SpecialAttention attention = new org.example.easychat.Entity.SpecialAttention();
            attention.setId(updateDTO.getId());

            NotificationSettings settings = updateDTO.getNotificationSettings();
            if (settings != null) {
                attention.setOnlineNotification(settings.getOnlineNotification());
                attention.setOfflineNotification(settings.getOfflineNotification());
                attention.setMessageNotification(settings.getMessageNotification());
                attention.setStatusChangeNotification(settings.getStatusChangeNotification());
            }

            attention.setUpdateTime(LocalDateTime.now());

            int updated = attentionMapper.updateById(attention);
            if (updated == 0) {
                throw new BusinessException(ErrorCode.ATTENTION_NOT_FOUND);
            }

            // 清除缓存
            clearAttentionCache(updateDTO.getUserId());

            log.info("更新通知设置成功: id={}, userId={}", updateDTO.getId(), updateDTO.getUserId());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新通知设置失败: id={}, userId={}", updateDTO.getId(), updateDTO.getUserId(), e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "更新通知设置失败");
        }
    }

    /**
     * 批量操作特别关心
     */
    @Override
    public BatchOperationResult batchOperation(AttentionBatchDTO batchDTO) {
        // 验证参数
        validateBatchParams(batchDTO);

        BatchOperationResult result = new BatchOperationResult();
        List<BatchOperationResult.OperationResult> results = new ArrayList<>();

        log.info("开始批量操作: userId={}, operation={}, targetCount={}",
                batchDTO.getUserId(), batchDTO.getOperation(), batchDTO.getTargetUserIds().size());

        for (String targetUserId : batchDTO.getTargetUserIds()) {
            try {
                if (AttentionBatchDTO.BatchOperationType.ADD.equals(batchDTO.getOperation())) {
                    // 添加特别关心
                    AttentionAddDTO addDTO = new AttentionAddDTO();
                    addDTO.setUserId(batchDTO.getUserId());
                    addDTO.setTargetUserId(targetUserId);
                    addDTO.setNotificationSettings(batchDTO.getNotificationSettings());

                    SpecialAttentionVO vo = addAttention(addDTO);
                    results.add(new BatchOperationResult.OperationResult(targetUserId, true, vo));
                    result.setSuccessCount(result.getSuccessCount() + 1);

                } else if (AttentionBatchDTO.BatchOperationType.REMOVE.equals(batchDTO.getOperation())) {
                    // 删除特别关心
                    AttentionRemoveDTO removeDTO = new AttentionRemoveDTO();
                    removeDTO.setUserId(batchDTO.getUserId());
                    removeDTO.setTargetUserId(targetUserId);

                    removeAttention(removeDTO);
                    results.add(new BatchOperationResult.OperationResult(targetUserId, true, null));
                    result.setSuccessCount(result.getSuccessCount() + 1);
                }
            } catch (Exception e) {
                log.warn("批量操作单项失败: userId={}, targetUserId={}, operation={}, error={}",
                        batchDTO.getUserId(), targetUserId, batchDTO.getOperation(), e.getMessage());
                results.add(new BatchOperationResult.OperationResult(targetUserId, false, e.getMessage()));
                result.setFailCount(result.getFailCount() + 1);
            }
        }

        result.setResults(results);
        log.info("批量操作完成: userId={}, operation={}, success={}, fail={}",
                batchDTO.getUserId(), batchDTO.getOperation(), result.getSuccessCount(), result.getFailCount());

        return result;
    }

    /**
     * 检查特别关心状态
     */
    @Override
    public AttentionCheckResult checkAttentionStatus(String userId, String targetUserId) {
        // 参数验证
        if (StringUtils.isBlank(userId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "用户ID不能为空");
        }
        if (StringUtils.isBlank(targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "目标用户ID不能为空");
        }

        try {
            org.example.easychat.Entity.SpecialAttention attention = attentionMapper.selectOne(
                    new LambdaQueryWrapper<org.example.easychat.Entity.SpecialAttention>()
                            .eq(org.example.easychat.Entity.SpecialAttention::getUserId, userId)
                            .eq(org.example.easychat.Entity.SpecialAttention::getTargetUserId, targetUserId)
            );

            AttentionCheckResult result = new AttentionCheckResult();
            result.setIsAttention(attention != null);

            if (attention != null) {
                AttentionCheckResult.AttentionInfo info = getAttentionInfo(attention);
                result.setAttentionInfo(info);
            }

            log.debug("检查特别关心状态: userId={}, targetUserId={}, isAttention={}",
                    userId, targetUserId, result.getIsAttention());

            return result;

        } catch (Exception e) {
            log.error("检查特别关心状态失败: userId={}, targetUserId={}", userId, targetUserId, e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "检查特别关心状态失败");
        }
    }

    /**
     * 获取特别关心更新
     */
    @Override
    public AttentionUpdatesResult getAttentionUpdates(AttentionUpdatesDTO updatesDTO) {
        // 参数验证
        if (StringUtils.isBlank(updatesDTO.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "用户ID不能为空");
        }

        try {
            // 实现获取更新逻辑
            // 这里可以从缓存或数据库中获取指定时间之后的更新
            AttentionUpdatesResult result = new AttentionUpdatesResult();

            // 获取指定时间之后的特别关心记录更新
            LambdaQueryWrapper<org.example.easychat.Entity.SpecialAttention> wrapper =
                    new LambdaQueryWrapper<org.example.easychat.Entity.SpecialAttention>()
                            .eq(org.example.easychat.Entity.SpecialAttention::getUserId, updatesDTO.getUserId());

            if (updatesDTO.getLastUpdateTime() != null) {
                wrapper.gt(org.example.easychat.Entity.SpecialAttention::getUpdateTime, updatesDTO.getLastUpdateTime());
            }

            List<org.example.easychat.Entity.SpecialAttention> updates = attentionMapper.selectList(wrapper);

            // 转换为VO
            List<SpecialAttentionVO> updateVOs = new ArrayList<>();
            for (org.example.easychat.Entity.SpecialAttention attention : updates) {
                updateVOs.add(buildAttentionVO(attention));
            }

            // 转换为AttentionUpdate格式
            List<AttentionUpdatesResult.AttentionUpdate> attentionUpdates = new ArrayList<>();
            for (SpecialAttentionVO vo : updateVOs) {
                AttentionUpdatesResult.AttentionUpdate update = new AttentionUpdatesResult.AttentionUpdate();
                update.setUpdateType("update");
                update.setTargetUserId(vo.getTargetUserId());
                update.setData(vo);
                update.setUpdateTime(vo.getUpdateTime());
                attentionUpdates.add(update);
            }

            result.setUpdates(attentionUpdates);
            result.setHasMore(updates.size() >= 50); // 假设每次最多返回50条
            result.setLastUpdateTime(LocalDateTime.now());

            log.debug("获取特别关心更新: userId={}, updateCount={}", updatesDTO.getUserId(), updates.size());
            return result;

        } catch (Exception e) {
            log.error("获取特别关心更新失败: userId={}", updatesDTO.getUserId(), e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "获取特别关心更新失败");
        }
    }

    private static AttentionCheckResult.AttentionInfo getAttentionInfo(org.example.easychat.Entity.SpecialAttention attention) {
        AttentionCheckResult.AttentionInfo info = new AttentionCheckResult.AttentionInfo();
        info.setId(attention.getId());
        info.setCreateTime(attention.getCreateTime());
        NotificationSettings settings = new NotificationSettings();
        settings.setOnlineNotification(attention.getOnlineNotification());
        settings.setMessageNotification(attention.getMessageNotification());
        settings.setStatusChangeNotification(attention.getStatusChangeNotification());
        info.setNotificationSettings(settings);
        return info;
    }

    private SpecialAttentionVO buildAttentionVO(org.example.easychat.Entity.SpecialAttention attention) {
        SpecialAttentionVO vo = new SpecialAttentionVO();
        BeanUtils.copyProperties(attention, vo);

        // 获取目标用户信息
        User targetUser = userMapper.selectById(attention.getTargetUserId());
        if (targetUser != null) {
            SpecialAttentionVO.TargetUserInfo userInfo = new SpecialAttentionVO.TargetUserInfo();
            userInfo.setAvatar(targetUser.getAvatar());
            userInfo.setNickName(targetUser.getNickName());
            userInfo.setRemark(targetUser.getIntroduction());

            // 获取用户状态
            UserStatus status = userStatusService.getUserStatus(attention.getTargetUserId());
            if (status != null) {
                userInfo.setStatus(status.getStatus());
                userInfo.setLastActiveTime(status.getLastActiveTime().toString());
            }

            vo.setTargetUserInfo(userInfo);
        }

        // 设置通知设置
        NotificationSettings settings = new NotificationSettings();
        settings.setOnlineNotification(attention.getOnlineNotification());
        settings.setMessageNotification(attention.getMessageNotification());
        settings.setStatusChangeNotification(attention.getStatusChangeNotification());
        vo.setNotificationSettings(settings);

        return vo;
    }

    // ==================== 私有验证方法 ====================

    /**
     * 验证查询参数
     */
    private void validateQueryParams(AttentionQueryDTO queryDTO) {
        if (StringUtils.isBlank(queryDTO.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "用户ID不能为空");
        }
        if (queryDTO.getPage() < 1) {
            queryDTO.setPage(1);
        }
        if (queryDTO.getPageSize() < 1 || queryDTO.getPageSize() > 100) {
            queryDTO.setPageSize(20);
        }
    }

    /**
     * 验证添加参数
     */
    private void validateAddParams(AttentionAddDTO addDTO) {
        if (StringUtils.isBlank(addDTO.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "用户ID不能为空");
        }
        if (StringUtils.isBlank(addDTO.getTargetUserId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "目标用户ID不能为空");
        }
        if (addDTO.getUserId().equals(addDTO.getTargetUserId())) {
            throw new BusinessException(ErrorCode.CANNOT_ATTENTION_SELF);
        }

        // 检查目标用户是否存在
        User targetUser = userMapper.selectById(addDTO.getTargetUserId());
        if (targetUser == null) {
            throw new BusinessException(ErrorCode.TARGET_USER_NOT_FOUND);
        }
    }

    /**
     * 验证删除参数
     */
    private void validateRemoveParams(AttentionRemoveDTO removeDTO) {
        if (StringUtils.isBlank(removeDTO.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "用户ID不能为空");
        }
        if (removeDTO.getId() == null && StringUtils.isBlank(removeDTO.getTargetUserId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "必须提供ID或目标用户ID");
        }
    }

    /**
     * 验证更新参数
     */
    private void validateUpdateParams(AttentionUpdateDTO updateDTO) {
        if (updateDTO.getId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "特别关心记录ID不能为空");
        }
        if (StringUtils.isBlank(updateDTO.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "用户ID不能为空");
        }
        if (updateDTO.getNotificationSettings() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "通知设置不能为空");
        }
    }

    /**
     * 验证批量操作参数
     */
    private void validateBatchParams(AttentionBatchDTO batchDTO) {
        if (StringUtils.isBlank(batchDTO.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "用户ID不能为空");
        }
        if (batchDTO.getOperationType() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "操作类型不能为空");
        }
        if (batchDTO.getOperationType() != AttentionBatchDTO.BatchOperationType.ADD && 
            batchDTO.getOperationType() != AttentionBatchDTO.BatchOperationType.REMOVE) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "操作类型必须是ADD或REMOVE");
        }
        if (batchDTO.getTargetUserIds() == null || batchDTO.getTargetUserIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "目标用户ID列表不能为空");
        }
        if (batchDTO.getTargetUserIds().size() > 50) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "批量操作数量不能超过50个");
        }

        // 检查是否包含自己
        if (batchDTO.getTargetUserIds().contains(batchDTO.getUserId())) {
            throw new BusinessException(ErrorCode.CANNOT_ATTENTION_SELF);
        }
    }

    /**
     * 获取特别关心统计信息
     */
    @Override
    public org.example.easychat.VO.AttentionStatistics getAttentionStatistics(String userId) {
        // 参数验证
        if (StringUtils.isBlank(userId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "用户ID不能为空");
        }

        try {
            org.example.easychat.VO.AttentionStatistics statistics = attentionMapper.getAttentionStatistics(userId);
            if (statistics == null) {
                statistics = new org.example.easychat.VO.AttentionStatistics();
                statistics.setUserId(userId);
                statistics.setTotalAttentions(0L);
                statistics.setOnlineAttentions(0L);
                statistics.setOfflineAttentions(0L);
                statistics.setTodayNewAttentions(0L);
            }

            log.debug("获取特别关心统计信息: userId={}, total={}", userId, statistics.getTotalAttentions());
            return statistics;

        } catch (Exception e) {
            log.error("获取特别关心统计信息失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "获取特别关心统计信息失败");
        }
    }

    /**
     * 清除特别关心缓存
     */
    private void clearAttentionCache(String userId) {
        try {
            String cacheKey = ATTENTION_CACHE_KEY + userId;
            redisTemplate.delete(cacheKey);
            log.debug("清除特别关心缓存: userId={}", userId);
        } catch (Exception e) {
            log.warn("清除特别关心缓存失败: userId={}", userId, e);
        }
    }


}

package org.example.easychat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.easychat.Handler.ErrorCode;
import org.example.easychat.Entity.SpecialAttention;
import org.example.easychat.Entity.UserStatus;
import org.example.easychat.Handler.BusinessException;
import org.example.easychat.Mapper.SpecialAttentionMapper;
import org.example.easychat.Mapper.UserStatusMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserStatusService {
    
    @Autowired
    private UserStatusMapper userStatusMapper;
    
    @Autowired
    private SpecialAttentionMapper attentionMapper;
    
    @Autowired
    private org.example.easychat.event.AttentionEventPublisher eventPublisher;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private UserService userService;
    
    private static final String USER_STATUS_KEY = "user:status:";
    private static final String ONLINE_USERS_KEY = "online:users";
    
    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime(String userId) {
        if (StringUtils.isBlank(userId)) {
            return;
        }
        
        try {
            UserStatus userStatus = getUserStatus(userId);
            if (userStatus != null) {
                userStatus.setLastActiveTime(LocalDateTime.now());
                userStatus.setUpdateTime(LocalDateTime.now());
                userStatusMapper.updateById(userStatus);
                
                // 更新缓存
                redisTemplate.opsForValue().set(USER_STATUS_KEY + userId, userStatus, Duration.ofHours(24));
            }
        } catch (Exception e) {
            log.error("更新用户最后活跃时间失败: userId={}", userId, e);
        }
    }
    
    /**
     * 获取指定用户列表中的在线用户
     */
    public List<org.example.easychat.dto.OnlineUserInfo> getOnlineUsersFromList(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            List<org.example.easychat.dto.OnlineUserInfo> onlineUsers = new ArrayList<>();
            
            // 批量获取用户状态
            Map<String, UserStatus> statusMap = batchGetUserStatus(userIds);
            
            for (String userId : userIds) {
                UserStatus status = statusMap.get(userId);
                if (status != null && "online".equals(status.getStatus())) {
                    // 获取用户基本信息
                    org.example.easychat.Entity.User user = getUserById(userId);
                    if (user != null) {
                        org.example.easychat.dto.OnlineUserInfo onlineUser = new org.example.easychat.dto.OnlineUserInfo();
                        onlineUser.setUserId(userId);
                        onlineUser.setUserName(user.getNickName());
                        onlineUser.setAvatar(user.getAvatar());
                        onlineUser.setStatus(status.getStatus());
                        onlineUser.setLastActiveTime(status.getLastActiveTime().toString());
                        onlineUsers.add(onlineUser);
                    }
                }
            }
            
            return onlineUsers;
        } catch (Exception e) {
            log.error("获取在线用户列表失败", e);
            return new ArrayList<>();
        }
    }
    

    
    /**
     * 获取用户基本信息
     */
    private org.example.easychat.Entity.User getUserById(String userId) {
        try {
            return userService.getUserById(userId);
        } catch (Exception e) {
            log.error("获取用户信息失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 更新用户状态
     */
    public void updateUserStatus(String userId, String status) {
        // 参数验证
        if (StringUtils.isBlank(userId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "用户ID不能为空");
        }
        if (StringUtils.isBlank(status)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "用户状态不能为空");
        }
        if (!isValidStatus(status)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "无效的用户状态: " + status);
        }
        
        try {
            // 获取当前状态
            UserStatus currentStatus = getUserStatus(userId);
            String oldStatus = currentStatus != null ? currentStatus.getStatus() : "offline";
            
            // 更新数据库
            UserStatus userStatus = new UserStatus();
            userStatus.setUserId(userId);
            userStatus.setStatus(status);
            userStatus.setLastActiveTime(LocalDateTime.now());
            userStatus.setUpdateTime(LocalDateTime.now());
            
            if (currentStatus == null) {
                userStatusMapper.insert(userStatus);
                log.info("创建用户状态记录: userId={}, status={}", userId, status);
            } else {
                userStatusMapper.updateById(userStatus);
                log.debug("更新用户状态: userId={}, oldStatus={}, newStatus={}", userId, oldStatus, status);
            }
            
            // 更新缓存
            redisTemplate.opsForValue().set(USER_STATUS_KEY + userId, userStatus, Duration.ofHours(24));
            
            // 处理在线用户集合
            if ("online".equals(status)) {
                redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId);
            } else {
                redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId);
            }
            
            // 如果状态发生变化，通知关心该用户的其他用户
            if (!oldStatus.equals(status)) {
                notifyStatusChange(userId, oldStatus, status);
            }
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新用户状态失败: userId={}, status={}", userId, status, e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "更新用户状态失败");
        }
    }
    
    /**
     * 获取用户状态
     */
    public UserStatus getUserStatus(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        
        try {
            // 先从缓存获取
            UserStatus status = (UserStatus) redisTemplate.opsForValue().get(USER_STATUS_KEY + userId);
            if (status != null) {
                log.debug("从缓存获取用户状态: userId={}, status={}", userId, status.getStatus());
                return status;
            }
            
            // 从数据库获取
            status = userStatusMapper.selectById(userId);
            if (status != null) {
                // 更新缓存
                redisTemplate.opsForValue().set(USER_STATUS_KEY + userId, status, Duration.ofHours(24));
                log.debug("从数据库获取用户状态并更新缓存: userId={}, status={}", userId, status.getStatus());
            } else {
                log.debug("用户状态不存在: userId={}", userId);
            }
            
            return status;
            
        } catch (Exception e) {
            log.error("获取用户状态失败: userId={}", userId, e);
            return null;
        }
    }
    
    /**
     * 获取在线用户列表
     */
    public Set<String> getOnlineUsers() {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
            if (members == null) {
                return new HashSet<>();
            }
            
            Set<String> onlineUsers = members.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
            
            log.debug("获取在线用户列表: count={}", onlineUsers.size());
            return onlineUsers;
            
        } catch (Exception e) {
            log.error("获取在线用户列表失败", e);
            return new HashSet<>();
        }
    }
    
    /**
     * 批量获取用户状态
     */
    public Map<String, UserStatus> batchGetUserStatus(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            Map<String, UserStatus> result = new HashMap<>();
            
            // 过滤有效的用户ID
            List<String> validUserIds = userIds.stream()
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .collect(Collectors.toList());
            
            if (validUserIds.isEmpty()) {
                return result;
            }
            
            // 批量从缓存获取
            List<String> keys = validUserIds.stream()
                .map(id -> USER_STATUS_KEY + id)
                .collect(Collectors.toList());
            
            List<Object> cachedStatuses = redisTemplate.opsForValue().multiGet(keys);
            
            List<String> missedUserIds = new ArrayList<>();
            for (int i = 0; i < validUserIds.size(); i++) {
                String userId = validUserIds.get(i);
                Object cached = cachedStatuses != null && i < cachedStatuses.size() ? cachedStatuses.get(i) : null;
                
                if (cached != null) {
                    result.put(userId, (UserStatus) cached);
                } else {
                    missedUserIds.add(userId);
                }
            }
            
            // 从数据库获取缓存中没有的数据
            if (!missedUserIds.isEmpty()) {
                List<UserStatus> dbStatuses = userStatusMapper.batchGetUserStatus(missedUserIds);
                for (UserStatus status : dbStatuses) {
                    result.put(status.getUserId(), status);
                    // 更新缓存
                    redisTemplate.opsForValue().set(
                        USER_STATUS_KEY + status.getUserId(), 
                        status, 
                        Duration.ofHours(24)
                    );
                }
            }
            
            log.debug("批量获取用户状态: requestCount={}, cacheHit={}, dbQuery={}, resultCount={}", 
                    validUserIds.size(), validUserIds.size() - missedUserIds.size(), 
                    missedUserIds.size(), result.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("批量获取用户状态失败: userIds={}", userIds, e);
            return new HashMap<>();
        }
    }
    
    /**
     * 通知状态变化
     */
    private void notifyStatusChange(String userId, String oldStatus, String newStatus) {
        try {
            // 使用事件发布器发布状态变化事件，避免循环依赖
            eventPublisher.publishUserStatusChangeEvent(userId, oldStatus, newStatus);
            
            // 发布具体的上线/离线事件
            if ("online".equals(newStatus)) {
                eventPublisher.publishUserOnlineEvent(userId);
            } else if ("offline".equals(newStatus)) {
                eventPublisher.publishUserOfflineEvent(userId);
            }
            
            log.debug("发布用户状态变化事件: userId={}, oldStatus={}, newStatus={}", userId, oldStatus, newStatus);
            
        } catch (Exception e) {
            log.error("发布用户状态变化事件失败: userId={}, oldStatus={}, newStatus={}", userId, oldStatus, newStatus, e);
        }
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 验证状态是否有效
     */
    private boolean isValidStatus(String status) {
        return "online".equals(status) || "offline".equals(status) || 
               "away".equals(status) || "busy".equals(status);
    }
    
    /**
     * 批量更新用户状态
     */
    public void batchUpdateUserStatus(Map<String, String> userStatusMap) {
        if (userStatusMap == null || userStatusMap.isEmpty()) {
            return;
        }
        
        try {
            List<UserStatus> statusList = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            
            for (Map.Entry<String, String> entry : userStatusMap.entrySet()) {
                String userId = entry.getKey();
                String status = entry.getValue();
                
                if (StringUtils.isNotBlank(userId) && isValidStatus(status)) {
                    UserStatus userStatus = new UserStatus();
                    userStatus.setUserId(userId);
                    userStatus.setStatus(status);
                    userStatus.setLastActiveTime(now);
                    userStatus.setUpdateTime(now);
                    statusList.add(userStatus);
                }
            }
            
            if (!statusList.isEmpty()) {
                // 批量更新数据库
                userStatusMapper.batchUpdateStatus(statusList);
                
                // 批量更新缓存
                for (UserStatus status : statusList) {
                    redisTemplate.opsForValue().set(
                            USER_STATUS_KEY + status.getUserId(), 
                            status, 
                            Duration.ofHours(24)
                    );
                    
                    // 更新在线用户集合
                    if ("online".equals(status.getStatus())) {
                        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, status.getUserId());
                    } else {
                        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, status.getUserId());
                    }
                }
                
                log.info("批量更新用户状态成功: count={}", statusList.size());
            }
            
        } catch (Exception e) {
            log.error("批量更新用户状态失败: userStatusMap={}", userStatusMap, e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "批量更新用户状态失败");
        }
    }
    
    /**
     * 清理长时间未活跃的用户状态
     */
    public void cleanInactiveUsers(int hours) {
        try {
            LocalDateTime before = LocalDateTime.now().minusHours(hours);
            int cleaned = userStatusMapper.cleanInactiveUsers(before);
            
            if (cleaned > 0) {
                log.info("清理长时间未活跃用户状态: count={}, beforeTime={}", cleaned, before);
            }
            
        } catch (Exception e) {
            log.error("清理长时间未活跃用户状态失败: hours={}", hours, e);
        }
    }
    
    /**
     * 获取用户在线时长统计
     */
    public long getOnlineUserCount() {
        try {
            Long count = redisTemplate.opsForSet().size(ONLINE_USERS_KEY);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("获取在线用户数量失败", e);
            return 0;
        }
    }
    
    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(String userId) {
        if (StringUtils.isBlank(userId)) {
            return false;
        }
        
        try {
            Boolean isMember = redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, userId);
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            log.error("检查用户在线状态失败: userId={}", userId, e);
            return false;
        }
    }
}
package org.example.easychat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 缓存服务 - 统一管理Redis缓存操作
 */
@Slf4j
@Service
public class CacheService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // 缓存键前缀常量
    public static final String USER_STATUS_PREFIX = "user:status:";
    public static final String ATTENTION_LIST_PREFIX = "attention:list:";
    public static final String ATTENTION_COUNT_PREFIX = "attention:count:";
    public static final String ONLINE_USERS_KEY = "online:users";
    public static final String USER_ATTENTION_PREFIX = "user:attention:";
    public static final String ATTENTION_SETTINGS_PREFIX = "attention:settings:";
    
    // 缓存过期时间常量
    public static final Duration USER_STATUS_TTL = Duration.ofHours(24);
    public static final Duration ATTENTION_LIST_TTL = Duration.ofMinutes(30);
    public static final Duration ATTENTION_COUNT_TTL = Duration.ofHours(1);
    public static final Duration ATTENTION_SETTINGS_TTL = Duration.ofHours(6);
    
    /**
     * 设置缓存
     */
    public void set(String key, Object value, Duration ttl) {
        try {
            if (ttl != null) {
                redisTemplate.opsForValue().set(key, value, ttl);
            } else {
                redisTemplate.opsForValue().set(key, value);
            }
            log.debug("设置缓存成功: key={}, ttl={}", key, ttl);
        } catch (Exception e) {
            log.error("设置缓存失败: key={}", key, e);
        }
    }
    
    /**
     * 获取缓存
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null && clazz.isInstance(value)) {
                log.debug("获取缓存成功: key={}", key);
                return (T) value;
            }
            return null;
        } catch (Exception e) {
            log.error("获取缓存失败: key={}", key, e);
            return null;
        }
    }
    
    /**
     * 批量获取缓存
     */
    public List<Object> multiGet(List<String> keys) {
        try {
            List<Object> values = redisTemplate.opsForValue().multiGet(keys);
            log.debug("批量获取缓存: keyCount={}, hitCount={}", 
                    keys.size(), values != null ? values.stream().mapToInt(v -> v != null ? 1 : 0).sum() : 0);
            return values;
        } catch (Exception e) {
            log.error("批量获取缓存失败: keys={}", keys, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 批量设置缓存
     */
    public void multiSet(Map<String, Object> keyValues, Duration ttl) {
        try {
            redisTemplate.opsForValue().multiSet(keyValues);
            
            // 如果指定了TTL，需要单独设置过期时间
            if (ttl != null) {
                for (String key : keyValues.keySet()) {
                    redisTemplate.expire(key, ttl);
                }
            }
            
            log.debug("批量设置缓存成功: count={}, ttl={}", keyValues.size(), ttl);
        } catch (Exception e) {
            log.error("批量设置缓存失败: keyValues={}", keyValues.keySet(), e);
        }
    }
    
    /**
     * 删除缓存
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("删除缓存成功: key={}", key);
        } catch (Exception e) {
            log.error("删除缓存失败: key={}", key, e);
        }
    }
    
    /**
     * 批量删除缓存
     */
    public void delete(Collection<String> keys) {
        try {
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("批量删除缓存成功: count={}", keys.size());
            }
        } catch (Exception e) {
            log.error("批量删除缓存失败: keys={}", keys, e);
        }
    }
    
    /**
     * 检查缓存是否存在
     */
    public boolean exists(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("检查缓存存在性失败: key={}", key, e);
            return false;
        }
    }
    
    /**
     * 设置过期时间
     */
    public void expire(String key, Duration ttl) {
        try {
            redisTemplate.expire(key, ttl);
            log.debug("设置缓存过期时间: key={}, ttl={}", key, ttl);
        } catch (Exception e) {
            log.error("设置缓存过期时间失败: key={}, ttl={}", key, ttl, e);
        }
    }
    
    /**
     * 获取剩余过期时间
     */
    public long getExpire(String key) {
        try {
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return expire != null ? expire : -1;
        } catch (Exception e) {
            log.error("获取缓存过期时间失败: key={}", key, e);
            return -1;
        }
    }
    
    // ==================== Set操作 ====================
    
    /**
     * 添加到Set
     */
    public void setAdd(String key, Object... values) {
        try {
            redisTemplate.opsForSet().add(key, values);
            log.debug("添加到Set成功: key={}, count={}", key, values.length);
        } catch (Exception e) {
            log.error("添加到Set失败: key={}", key, e);
        }
    }
    
    /**
     * 从Set中移除
     */
    public void setRemove(String key, Object... values) {
        try {
            redisTemplate.opsForSet().remove(key, values);
            log.debug("从Set移除成功: key={}, count={}", key, values.length);
        } catch (Exception e) {
            log.error("从Set移除失败: key={}", key, e);
        }
    }
    
    /**
     * 检查Set中是否存在
     */
    public boolean setIsMember(String key, Object value) {
        try {
            Boolean isMember = redisTemplate.opsForSet().isMember(key, value);
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            log.error("检查Set成员失败: key={}, value={}", key, value, e);
            return false;
        }
    }
    
    /**
     * 获取Set所有成员
     */
    public Set<Object> setMembers(String key) {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(key);
            return members != null ? members : Collections.emptySet();
        } catch (Exception e) {
            log.error("获取Set成员失败: key={}", key, e);
            return Collections.emptySet();
        }
    }
    
    /**
     * 获取Set大小
     */
    public long setSize(String key) {
        try {
            Long size = redisTemplate.opsForSet().size(key);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.error("获取Set大小失败: key={}", key, e);
            return 0;
        }
    }
    
    // ==================== Hash操作 ====================
    
    /**
     * 设置Hash字段
     */
    public void hashSet(String key, String field, Object value) {
        try {
            redisTemplate.opsForHash().put(key, field, value);
            log.debug("设置Hash字段成功: key={}, field={}", key, field);
        } catch (Exception e) {
            log.error("设置Hash字段失败: key={}, field={}", key, field, e);
        }
    }
    
    /**
     * 获取Hash字段
     */
    @SuppressWarnings("unchecked")
    public <T> T hashGet(String key, String field, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForHash().get(key, field);
            if (value != null && clazz.isInstance(value)) {
                return (T) value;
            }
            return null;
        } catch (Exception e) {
            log.error("获取Hash字段失败: key={}, field={}", key, field, e);
            return null;
        }
    }
    
    /**
     * 批量设置Hash字段
     */
    public void hashMultiSet(String key, Map<String, Object> fieldValues) {
        try {
            redisTemplate.opsForHash().putAll(key, fieldValues);
            log.debug("批量设置Hash字段成功: key={}, count={}", key, fieldValues.size());
        } catch (Exception e) {
            log.error("批量设置Hash字段失败: key={}", key, e);
        }
    }
    
    /**
     * 获取Hash所有字段
     */
    public Map<Object, Object> hashGetAll(String key) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            return entries != null ? entries : Collections.emptyMap();
        } catch (Exception e) {
            log.error("获取Hash所有字段失败: key={}", key, e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * 删除Hash字段
     */
    public void hashDelete(String key, String... fields) {
        try {
            redisTemplate.opsForHash().delete(key, (Object[]) fields);
            log.debug("删除Hash字段成功: key={}, fields={}", key, Arrays.toString(fields));
        } catch (Exception e) {
            log.error("删除Hash字段失败: key={}, fields={}", key, Arrays.toString(fields), e);
        }
    }
    
    // ==================== 缓存失效策略 ====================
    
    /**
     * 清理用户相关缓存
     */
    public void clearUserCache(String userId) {
        try {
            List<String> keysToDelete = Arrays.asList(
                USER_STATUS_PREFIX + userId,
                ATTENTION_LIST_PREFIX + userId,
                ATTENTION_COUNT_PREFIX + userId,
                USER_ATTENTION_PREFIX + userId,
                ATTENTION_SETTINGS_PREFIX + userId
            );
            
            delete(keysToDelete);
            setRemove(ONLINE_USERS_KEY, userId);
            
            log.info("清理用户缓存成功: userId={}", userId);
        } catch (Exception e) {
            log.error("清理用户缓存失败: userId={}", userId, e);
        }
    }
    
    /**
     * 清理特别关心相关缓存
     */
    public void clearAttentionCache(String userId, String targetUserId) {
        try {
            List<String> keysToDelete = Arrays.asList(
                ATTENTION_LIST_PREFIX + userId,
                ATTENTION_COUNT_PREFIX + userId,
                ATTENTION_SETTINGS_PREFIX + userId + ":" + targetUserId
            );
            
            delete(keysToDelete);
            
            log.info("清理特别关心缓存成功: userId={}, targetUserId={}", userId, targetUserId);
        } catch (Exception e) {
            log.error("清理特别关心缓存失败: userId={}, targetUserId={}", userId, targetUserId, e);
        }
    }
    
    /**
     * 预热缓存 - 批量加载热点数据
     */
    public void warmUpCache(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        
        try {
            log.info("开始预热缓存: userCount={}", userIds.size());
            
            // 这里可以预加载用户状态、特别关心列表等热点数据
            // 具体实现可以根据业务需求调整
            
            log.info("缓存预热完成: userCount={}", userIds.size());
        } catch (Exception e) {
            log.error("缓存预热失败: userIds={}", userIds, e);
        }
    }
}
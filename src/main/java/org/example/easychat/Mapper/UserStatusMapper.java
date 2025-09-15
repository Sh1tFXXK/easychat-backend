package org.example.easychat.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.easychat.Entity.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserStatusMapper extends BaseMapper<UserStatus> {
    
    /**
     * 批量更新用户状态
     */
    int batchUpdateStatus(@Param("statusList") List<UserStatus> statusList);
    
    /**
     * 获取在线用户列表
     */
    List<String> getOnlineUsers();
    
    /**
     * 获取指定时间内活跃的用户
     */
    List<UserStatus> getActiveUsers(@Param("since") LocalDateTime since);
    
    /**
     * 清理长时间未活跃的用户状态
     */
    int cleanInactiveUsers(@Param("before") LocalDateTime before);
    
    /**
     * 根据用户ID获取用户状态
     */
    UserStatus getByUserId(@Param("userId") String userId);
    
    /**
     * 更新用户最后活跃时间
     */
    int updateLastActiveTime(@Param("userId") String userId, @Param("lastActiveTime") LocalDateTime lastActiveTime);
    
    /**
     * 批量获取用户状态
     */
    List<UserStatus> batchGetUserStatus(@Param("userIds") List<String> userIds);
    
    /**
     * 更新用户Socket ID
     */
    int updateSocketId(@Param("userId") String userId, @Param("socketId") String socketId);
    
    /**
     * 根据Socket ID获取用户状态
     */
    UserStatus getBySocketId(@Param("socketId") String socketId);
    
    /**
     * 清理断开连接的Socket ID
     */
    int clearSocketId(@Param("socketId") String socketId);
}
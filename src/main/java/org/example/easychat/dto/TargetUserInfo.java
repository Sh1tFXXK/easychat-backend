package org.example.easychat.dto;

import lombok.Data;

/**
 * 目标用户信息DTO
 */
@Data
public class TargetUserInfo {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户昵称
     */
    private String userName;
    
    /**
     * 用户头像
     */
    private String avatar;
    
    /**
     * 用户备注
     */
    private String remark;
    
    /**
     * 用户状态
     */
    private String status;
    
    /**
     * 最后活跃时间
     */
    private String lastActiveTime;
}
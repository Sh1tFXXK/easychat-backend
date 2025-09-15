package org.example.easychat.dto;

import lombok.Data;

@Data
public class OnlineUserInfo {
    private String userId;
    private String userName;
    private String avatar;
    private String status;
    private String lastActiveTime;
}
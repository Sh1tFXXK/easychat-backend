package org.example.easychat.dto;

import lombok.Data;

@Data
public class AttentionUserOfflineEvent {
    private String userId;
    private String userName;
    private String avatar;
    private String lastActiveTime;
    private String timestamp;
}
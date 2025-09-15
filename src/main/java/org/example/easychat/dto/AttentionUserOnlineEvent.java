package org.example.easychat.dto;

import lombok.Data;

@Data
public class AttentionUserOnlineEvent {
    private String userId;
    private String userName;
    private String avatar;
    private String timestamp;
}
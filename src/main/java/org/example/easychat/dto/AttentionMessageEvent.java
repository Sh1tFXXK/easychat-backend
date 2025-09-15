package org.example.easychat.dto;

import lombok.Data;

@Data
public class AttentionMessageEvent {
    private String userId;
    private String userName;
    private String avatar;
    private String messagePreview;
    private String messageId;
    private String timestamp;
}
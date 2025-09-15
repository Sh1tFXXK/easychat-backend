package org.example.easychat.dto;

import lombok.Data;

@Data
public class AttentionStatusChangeEvent {
    private String userId;
    private String userName;
    private String avatar;
    private String newStatus;
    private String previousStatus;
    private String timestamp;
}
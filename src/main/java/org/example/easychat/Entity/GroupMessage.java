package org.example.easychat.Entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class GroupMessage {

    private String groupId;
    private String senderId;
    private String content;
    private String messageType;
    private Timestamp sentAt;
    private String messageId;
    private String senderUsername;
}

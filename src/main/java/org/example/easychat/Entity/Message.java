package org.example.easychat.Entity;

import lombok.Data;

@Data
public class Message {
    private String senderId;
    private String receiverId;
    private String sessionId;
    private Integer type;
    private String content;
    private Integer hasRead;
    private Integer showTime;
    private String createTime;
}

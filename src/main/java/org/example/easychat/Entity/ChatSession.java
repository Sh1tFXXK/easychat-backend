package org.example.easychat.Entity;

import lombok.Data;

@Data
public class ChatSession {
    private String sessionId;
    private String userId;
    private String friendUserId;
    private String friendRemark;
    private String friendNickName;
    private String friendAvatar;
    private String createTime;
    private ChatHistory latestChatHistory;


}

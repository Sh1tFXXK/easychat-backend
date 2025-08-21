package org.example.easychat.Entity;

import lombok.Data;

@Data
public class FriendInfo {
    private String userId;
    private String friendUserId;
    private String friendRemark;
    private String createTime;
    private String sessionId;
}

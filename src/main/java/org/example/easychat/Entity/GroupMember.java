package org.example.easychat.Entity;

import lombok.Data;

@Data
public class GroupMember {
    private String userId;
    private String groupId;
    private String JoinedAt;
    private String role;
}

package org.example.easychat.Entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class Group {

    private String groupId;
    private String groupName;
    private String ownerId;
    private String announcement;
    private String avatar;
    private String createdAt;

}

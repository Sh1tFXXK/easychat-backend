package org.example.easychat.dto;

import lombok.Data;

@Data
public class createGroupDto {
    private String groupName;
    private String groupId ;
    private String ownerId;
    private String createdAt;
}

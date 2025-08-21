package org.example.easychat.dto;


import lombok.Data;


import java.time.LocalDateTime;
import java.util.List;

@Data
public class friendsDto {
    private  String userId;
    private String friendUserId;
    private String friendRemark;
    private String friendAvatar;
    private String introduction;
    private List<String> friendTags;
    private String sessionId;
    private LocalDateTime sessionTime;
}

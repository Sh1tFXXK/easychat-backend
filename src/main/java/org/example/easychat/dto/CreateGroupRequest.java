package org.example.easychat.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class CreateGroupRequest {
    private String groupName;
    private List<String> initialMembers;

}

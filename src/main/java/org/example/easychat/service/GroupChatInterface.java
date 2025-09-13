package org.example.easychat.service;

import org.example.easychat.BO.ApiResponseBO;
import org.example.easychat.dto.createGroupDto;
import org.example.easychat.Entity.Group;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


public interface GroupChatInterface {
    createGroupDto createGroup(String groupName, List<String> initialMembers);

    List<Group> getUserGroups(String userId);

    Group getGroupDetail(String groupId);

    void inviteMembers(String groupId, List<String> userIds);

    void removeMember(String groupId, String userId);

    void updateGroup(String groupId, String groupName, String announcement, String avatar);

    ApiResponseBO sendImage(MultipartFile file, String groupId,String userId);

    ApiResponseBO sendFile(MultipartFile file, String groupId, String userId);

    void deleteGroup(String groupId);
}

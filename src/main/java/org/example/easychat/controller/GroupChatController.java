package org.example.easychat.controller;

import org.apache.ibatis.annotations.Delete;
import org.example.easychat.BO.ApiResponseBO;
import org.example.easychat.BO.ResponseBO;
import org.example.easychat.Entity.User;
import org.example.easychat.dto.CreateGroupRequest;
import org.example.easychat.dto.createGroupDto;
import org.example.easychat.service.GroupChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.example.easychat.Entity.Group;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * 群聊
 */
@RestController
@RequestMapping("/group")
public class GroupChatController {

    @Autowired
    private GroupChatService groupChat;
    /**
     * 创建群聊
     */
    @PostMapping("groups")
    public ResponseBO<createGroupDto> createGroup(@RequestBody CreateGroupRequest request, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        String currentUserId = currentUser.getId();
        // 确保群主（当前用户）在成员列表中
        List<String> membersWithOwner = new ArrayList<>(request.getInitialMembers());
        if (!membersWithOwner.contains(currentUserId)) {
            membersWithOwner.add(0, currentUserId); // 将群主添加为第一个成员
        } else {
            // 如果当前用户已经在列表中，确保其位置在第一位
            membersWithOwner.remove(currentUserId);
            membersWithOwner.add(0, currentUserId);
        }
        
        createGroupDto newGroup = groupChat.createGroup(request.getGroupName(), membersWithOwner);
        return ResponseBO.success("群聊创建成功", newGroup);
    }
    /**
     * 解散群聊
     */
    @DeleteMapping("groups/{groupId}")
    public ResponseBO<String> deleteGroup(@PathVariable String groupId) {
        groupChat.deleteGroup(groupId);
        return ResponseBO.success("群聊解散成功");
    }
    /**
     * 获取用户加入的群聊列表
     */
    @GetMapping("users/{userId}/groups")
    public ResponseBO<List<Group>> getUserGroups(@PathVariable String userId) {
        List<Group> groups = groupChat.getUserGroups(userId);
        return ResponseBO.success("获取用户加入的群聊列表成功", groups);
    }

    /**
     * 获取群详情
     */
    @GetMapping("groups/{groupId}")
    public ResponseBO<Group> getGroupDetail(@PathVariable String groupId) {
        Group g = groupChat.getGroupDetail(groupId);
        return ResponseBO.success("获取群详情成功", g);
    }
    /**
     * 邀请成员
     */
    @PostMapping("groups/{groupId}/members")
    public  ResponseBO<String> inviteMembers(@PathVariable String groupId, @RequestBody List<String>  userIds) {
        groupChat.inviteMembers(groupId, userIds);
        return ResponseBO.success("邀请成员成功");
    }
    /**
     * 移除成员
      */
    @DeleteMapping("groups/{groupId}/members/{userId}")
    public ResponseBO<String> removeMember(@PathVariable String groupId, @PathVariable String userId) {
        groupChat.removeMember(groupId, userId);
        return ResponseBO.success("移除成员成功");
    }
    /**
     * 更新群信息
     */
    @PutMapping("groups/{groupId}")
    public ResponseBO<String> updateGroup(@PathVariable String groupId, @RequestBody Map<String, String> payload) {
        String groupName = payload.get("groupName");
        String announcement = payload.get("announcement");
        String avatar = payload.get("avatar");
        groupChat.updateGroup(groupId, groupName, announcement, avatar);
        return ResponseBO.success("更新群信息成功");
    }

    /**
     * 获取群聊历史消息 (分页)
     */
    @GetMapping("groups/{groupId}/messages")
    public ResponseBO<Map<String, Object>> getGroupMessages(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Authentication authentication) {

        User currentUser = (User) authentication.getPrincipal();
        String currentUserId = currentUser.getId();

        // 验证用户是否是群成员
        if (!groupChat.isGroupMember(groupId, currentUserId)) {
            return ResponseBO.error("您不是该群成员，无法查看消息记录");
        }

        // 获取分页消息数据
        Map<String, Object> result = groupChat.getGroupMessages(groupId, page, pageSize);
        return ResponseBO.success("获取群聊历史消息成功", result);
    }
    /**
     * 发送群聊图片消息
     */
    @PostMapping("messages/image")
    public ApiResponseBO sendImage(@RequestParam("file") MultipartFile file,
                                     @RequestParam("groupId") String groupId,
                                   Authentication  authentication){
        User currentUser = (User) authentication.getPrincipal();
        String currentUserId = currentUser.getId();
        return groupChat.sendImage(file, groupId, currentUserId);
    }
    /**
     * 发送群聊文件消息
     */
    @PostMapping("messages/file")
    public ApiResponseBO sendFile(@RequestParam("file") MultipartFile file,
                                   @RequestParam("groupId") String groupId,
                                   Authentication  authentication){
        User currentUser = (User) authentication.getPrincipal();
        String currentUserId = currentUser.getId();
        return groupChat.sendFile(file, groupId, currentUserId);
    }

//    @DeleteMapping("groups/{groupId}")
//    public ResponseBO<String> deleteGroup(@PathVariable String groupId) {
//        //todo
//    }

}

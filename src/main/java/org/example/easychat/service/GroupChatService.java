package org.example.easychat.service;

import com.corundumstudio.socketio.SocketIOServer;
import org.example.easychat.BO.ApiResponseBO;
import org.example.easychat.Entity.GroupMember;
import org.example.easychat.Entity.GroupMessage;
import org.example.easychat.Handler.ChatSocketIOHandler;
import org.example.easychat.Mapper.GroupChatMapper;
import org.example.easychat.dto.createGroupDto;
import org.example.easychat.utils.AliOSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.easychat.Entity.Group;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GroupChatService implements GroupChatInterface{

    @Autowired
    private GroupChatMapper groupChatMapper;

    @Autowired
    private AliOSSUtil aliOSSUtil;

    @Autowired
    private ChatSocketIOHandler chatSocketIOHandler;

    @Override
    @Transactional
    public createGroupDto createGroup(String groupName, List<String> initialMembers) {

        // 创建群组逻辑
        createGroupDto groupDto = new createGroupDto();
        groupDto.setGroupName(groupName);
        groupDto.setOwnerId(initialMembers.get(0));

        // 保存群组信息
        groupChatMapper.insertGroup(groupDto);

        // 批量保存群组成员信息
        String gid = groupDto.getGroupId(); 
        List<GroupMember> groupMembers = new ArrayList<>();
        for (int i = 0; i < initialMembers.size(); i++) {
            String userId = initialMembers.get(i);
            String role = (i == 0) ? "owner" : "member"; // 第一个成员设为群主

            GroupMember member = new GroupMember();
            member.setGroupId(gid);
            member.setUserId(userId);
            member.setRole(role);
            groupMembers.add(member);
        }
        // 批量插入所有成员
        groupChatMapper.batchInsertGroupMembers(groupMembers);

        return groupDto;
    }

    @Override
    public List<Group> getUserGroups(String userId) {
       return groupChatMapper.selectGroupsByUserId(userId);
    }

    @Override
    public Group getGroupDetail(String groupId) {
        return groupChatMapper.selectGroupById(groupId);
    }
    @Override
    public void inviteMembers(String groupId, List<String> userIds) {
        groupChatMapper.batchInsertGroupMembersByIds(userIds);
    }

    public void removeMember(String groupId, String userId) {
        groupChatMapper.removeMember(groupId, userId);
    }

    public void updateGroup(String groupId, String groupName, String announcement, String avatar) {
        groupChatMapper.updateGroup(groupId, groupName, announcement, avatar);
    }

    public boolean isGroupMember(String groupId, String currentUserId) {
        return groupChatMapper.isGroupMember(groupId, currentUserId);
    }

    public Map<String, Object> getGroupMessages(String groupId, int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        List<GroupMessage> groupMessages = groupChatMapper.getGroupMessages(groupId);
        result.put("records", groupMessages);
        result.put("total", groupMessages.size());
        return result;
    }

    @Override
    public ApiResponseBO sendImage(MultipartFile file, String groupId, String userId) {
        //2.验证
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        long size = file.getSize();
        if (size > 30 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过30MB");
        }
        // 保存图片
        try {
            String url = aliOSSUtil.uploadImage(file);
            GroupMessage message = new GroupMessage();
            message.setGroupId(groupId);
            message.setContent(url);
            message.setMessageType("image");
            message.setSenderId(userId);
            message.setSentAt(new Timestamp(System.currentTimeMillis()));
            groupChatMapper.insertGroupMessage(message);
            
            // 通过WebSocket推送消息给所有群成员
            // 获取群成员列表
            List<String> groupMembers = groupChatMapper.getGroupMemberIds(groupId);
            
            // 推送消息给每个在线的群成员
            for (String memberId : groupMembers) {
                // 获取用户的Socket客户端
                com.corundumstudio.socketio.SocketIOClient client = chatSocketIOHandler.getUserClient(memberId);
                if (client != null && client.isChannelOpen()) {
                    client.sendEvent("receive_group_message", message);
                }
            }
            
            return ApiResponseBO.success(url);

        } catch (IOException e) {
            throw new RuntimeException("文件上传失败");
        }
    }
    @Override
    public ApiResponseBO sendFile(MultipartFile file, String groupId, String userId) {
        //2.验证
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        long size = file.getSize();
        if (size > 30 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过30MB");
        }
        // 保存图片
        try {
            String url = aliOSSUtil.uploadFile(file);
            GroupMessage message = new GroupMessage();
            message.setGroupId(groupId);
            message.setContent(url);
            message.setMessageType("file");
            message.setSenderId(userId);
            message.setSentAt(new Timestamp(System.currentTimeMillis()));
            groupChatMapper.insertGroupMessage(message);
            
            // 通过WebSocket推送消息给所有群成员
            // 获取群成员列表
            List<String> groupMembers = groupChatMapper.getGroupMemberIds(groupId);
            
            // 推送消息给每个在线的群成员
            for (String memberId : groupMembers) {
                // 获取用户的Socket客户端
                com.corundumstudio.socketio.SocketIOClient client = chatSocketIOHandler.getUserClient(memberId);
                if (client != null && client.isChannelOpen()) {
                    client.sendEvent("receive_group_message", message);
                }
            }
            
            return ApiResponseBO.success(url);

        } catch (IOException e) {
            throw new RuntimeException("文件上传失败");
        }
    }
    @Override
    public ApiResponseBO sendText(String content, String groupId, String userId) {
        // 验证内容
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        // 限制消息长度（可选）
        if (content.length() > 1000) {
            throw new IllegalArgumentException("消息内容不能超过1000个字符");
        }

        try {
            GroupMessage message = new GroupMessage();
            message.setGroupId(groupId);
            message.setContent(content);
            message.setMessageType("text");
            message.setSenderId(userId);
            message.setSentAt(new Timestamp(System.currentTimeMillis()));
            groupChatMapper.insertGroupMessage(message);
            
            // 通过WebSocket推送消息给所有群成员
            // 获取群成员列表
            List<String> groupMembers = groupChatMapper.getGroupMemberIds(groupId);
            
            // 推送消息给每个在线的群成员
            for (String memberId : groupMembers) {
                // 获取用户的Socket客户端
                com.corundumstudio.socketio.SocketIOClient client = chatSocketIOHandler.getUserClient(memberId);
                if (client != null && client.isChannelOpen()) {
                    client.sendEvent("receive_group_message", message);
                }
            }
            
            return ApiResponseBO.success("消息发送成功");
        } catch (Exception e) {
            throw new RuntimeException("消息发送失败: " + e.getMessage());
        }
    }

}

package org.example.easychat.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.easychat.Entity.GroupMember;
import org.example.easychat.Entity.GroupMessage;
import org.example.easychat.dto.createGroupDto;
import org.example.easychat.Entity.Group;


import java.util.List;

@Mapper
public interface GroupChatMapper extends BaseMapper<createGroupDto> {

     void insertGroup(createGroupDto groupDto) ;


    void batchInsertGroupMembers(List<GroupMember> groupMembers);

    List<Group> selectGroupsByUserId(String userId);

    Group selectGroupById(String groupId);

    List<GroupMember> selectGroupMembers(String groupId);

    void batchInsertGroupMembersByIds(List<String> userIds);

    @Delete("DELETE FROM group_members WHERE group_id = #{groupId} AND user_id = #{userId}")
    void removeMember(String groupId, String userId);

    void updateGroup(String groupId, String groupName, String announcement, String avatar);

    @Select("SELECT * FROM group_members WHERE group_id = #{groupId} AND user_id = #{currentUserId}")
    boolean isGroupMember(String groupId, String currentUserId);

    @Select("SELECT * FROM group_messages WHERE group_id = #{groupId}")
    List<GroupMessage> getGroupMessages(String groupId);
    
    @Select("SELECT user_id FROM group_members WHERE group_id = #{groupId}")
    List<String> getGroupMemberIds(String groupId);

    @Insert("INSERT INTO group_messages (group_id, sender_id, content, message_type, sent_at) VALUES (#{groupId}, #{senderId}, #{content}, #{messageType}, #{sentAt})")
    void insertGroupMessage(GroupMessage message);

    @Delete("DELETE FROM `groups` WHERE group_id = #{groupId}")
    void deleteGroup(String groupId);

    @Delete("DELETE FROM group_members WHERE group_id = #{groupId}")
    void deleteAllGroupMembers(String groupId);

    @Delete("DELETE FROM group_messages WHERE group_id = #{groupId}")
    void deleteAllGroupMessages(String groupId);
}

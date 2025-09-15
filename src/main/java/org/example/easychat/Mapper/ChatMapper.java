package org.example.easychat.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.easychat.Entity.*;

import java.util.List;

@Mapper
public interface ChatMapper extends BaseMapper<ChatHistory> {

    ChatHistory getLatestChatHistory(String userId);

    List<ChatSession> getChats(String userId);

    @Select("select * from chat_histories where session_id=#{sessionId}")
    List<ChatHistory> getChatHistory(String sessionId);

    @Select("SELECT COUNT(*) FROM chat_histories WHERE session_id = #{sessionId}")
    int getTotal(String sessionId);

    ChatHistory getLatestChatHistoryBySessionId(String sessionId);

    Message getLatestChatMessage(String sessionId);

    @Delete("delete from chat_sessions where user_id=#{userId} and friend_user_id=#{friendId}")
    void removeChat(String userId, String friendId);

    @Insert("INSERT INTO voice_messages (message_id, sender_id, receiver_id, file_name, file_url, duration, create_time, file_size, chat_type) " +
            "VALUES (#{messageId}, #{senderId}, #{receiverId}, #{fileName}, #{fileUrl}, #{duration}, #{createTime}, #{fileSize}, #{chatType})")
    VoiceMessage insertVoiceMessage(VoiceMessage message);

    List<String> getGroupMemberIds(String groupId);

    void insertCallRecord(CallRecord callRecord);
}
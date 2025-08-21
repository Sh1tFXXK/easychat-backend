package org.example.easychat.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.easychat.Entity.ChatSession;
import org.example.easychat.Entity.ChatHistory;
import org.example.easychat.Entity.Message;

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
}
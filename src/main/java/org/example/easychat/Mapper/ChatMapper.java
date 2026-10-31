package org.example.easychat.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
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

    // 优化：游标分页获取聊天记录（避免深度分页性能问题）
    @Select("SELECT id, sender_id, receiver_id, session_id, type, content, has_read, show_time, create_time " +
            "FROM chat_histories " +
            "WHERE session_id = #{sessionId} " +
            "AND (#{lastId} IS NULL OR id < #{lastId}) " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<ChatHistory> getChatHistoryCursor(@Param("sessionId") String sessionId,
                                           @Param("lastId") String lastId,
                                           @Param("limit") int limit);

    // 优化：离线消息批量拉取（按时间与未读状态）
    @Select("SELECT id, sender_id, receiver_id, session_id, type, content, has_read, show_time, create_time " +
            "FROM chat_histories " +
            "WHERE receiver_id = #{userId} AND has_read = 0 AND create_time > #{lastOnlineTime} " +
            "ORDER BY create_time ASC LIMIT #{limit}")
    List<ChatHistory> getOfflineMessages(@Param("userId") String userId,
                                         @Param("lastOnlineTime") String lastOnlineTime,
                                         @Param("limit") int limit);

    // 优化：按会话统计用户的未读消息数量（使用聚合与索引）
    @Select("SELECT session_id AS sessionId, COUNT(*) AS unreadCount " +
            "FROM chat_histories WHERE receiver_id = #{userId} AND has_read = 0 " +
            "GROUP BY session_id")
    List<UnreadCount> getUnreadCounts(@Param("userId") String userId);

    // 优化：批量标记会话为已读（用户维度）
    @Update("UPDATE chat_histories SET has_read = 1 WHERE session_id = #{sessionId} AND receiver_id = #{userId} AND has_read = 0")
    int batchMarkAsRead(@Param("sessionId") String sessionId, @Param("userId") String userId);

    // 优化：获取最近聊天会话列表（包含好友昵称与头像）
    @Select("SELECT cs.session_id AS sessionId, cs.user_id AS userId, cs.friend_user_id AS friendUserId, " +
            "cs.create_time AS createTime, u.nickname AS friendNickName, u.avatar AS friendAvatar " +
            "FROM chat_sessions cs LEFT JOIN users u ON cs.friend_user_id = u.id " +
            "WHERE cs.user_id = #{userId} ORDER BY cs.update_time DESC LIMIT #{limit}")
    List<ChatSession> getOptimizedChatList(@Param("userId") String userId, @Param("limit") int limit);

    interface UnreadCount {
        String getSessionId();
        Integer getUnreadCount();
    }
}
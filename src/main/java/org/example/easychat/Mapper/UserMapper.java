package org.example.easychat.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.example.easychat.Entity.FriendInfo;
import org.example.easychat.Entity.User;
import org.example.easychat.dto.friendVerifyDto;
import org.example.easychat.dto.friendsDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper extends BaseMapper<User> {

     void insertUser(User userInfo);


    User getUserByUsername(String username);

    void updateUserStatus(int status,String userId);

    @Select("select * from users where id = #{userId} limit 1")
    User getUserById(String userId);

    @Select("select tag from user_tags where user_id = #{userId}")
    List<String> getUserTags(String userId);

    @MapKey("friendUserId")
    Map<String, friendsDto> getUserFriendsBaseInfo(String userId);

    @MapKey("friendUserId")
    List<Map<String, Object>> findTagsForUserIds(@Param("userIds") List<String> userIds);

    List<String> getUserFriendsIds(String userId);

    List<String> getUserFriendVerifyIds(String userId);


    List<User> getUserFriends(String userId);


    List<friendVerifyDto> getFriendVerifications(String userId);

    @Insert("insert into user_tags (user_id, tag) values (#{userId}, #{tag})")
    void addTag(String userId, String tag);

    @Delete("delete from user_tags where user_id = #{userId} and tag = #{tag}")
    void removeTag(String userId, String tag);

    User getUserWithTagsById(String userId);

    void updateFriendStatus(String userId, String friendId, int i);


    void insertUserFriend(@Param("userId") String userId, @Param("friendInfo") FriendInfo friendInfo);

    void insertFriendVerify(friendVerifyDto friendVerifyDto);

    @Delete("delete from user_friends where user_id = #{userId} and friend_user_id = #{friendId}")
    void removeFriend(String userId, String friendId);

    boolean isUserInGroup(String groupId, String senderId);
}

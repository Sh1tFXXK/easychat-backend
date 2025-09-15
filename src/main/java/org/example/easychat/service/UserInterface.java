package org.example.easychat.service;

import org.example.easychat.Entity.PageResult;
import org.example.easychat.Entity.User;
import org.example.easychat.Entity.UserSearchResult;
import org.example.easychat.dto.RegisterDto;
import org.example.easychat.dto.editDto;
import org.example.easychat.dto.friendVerifyDto;
import org.example.easychat.dto.friendsDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface UserInterface {
    /**
     * 根据用户id获取用户信息
     *
     * @param userId
     * @return
     */
    User getUserById(String userId);

    /**
     * 发送验证码
     *
     * @param email
     * @param type
     */
    void sendVerifyCode(String email, String type);

    /**
     * 验证验证码
     *
     * @param email
     * @param code
     */
    void validateVerifyCode(String email, String code);

    void register(RegisterDto registerDto);

    UserSearchResult searchUser(String username);

    List<friendsDto> getFriendsList(String userId);

    List<friendVerifyDto> getFriendVerifyList(String userId);

    void editUser(editDto editDto);

    String updateUserAvatar(MultipartFile avatar, String userId) throws IOException;

    void changePassword(String userId, String checkPassword, String newPassword);

    boolean validatePassword(String userId, String password);

    Map<String, String> addTag(String userId, String tag);


}
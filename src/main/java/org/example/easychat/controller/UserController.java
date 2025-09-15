package org.example.easychat.controller;

import org.example.easychat.BO.ApiResponseBO;
import org.example.easychat.BO.ResponseBO;
import org.example.easychat.Entity.PageResult;
import org.example.easychat.Entity.User;
import org.example.easychat.Entity.UserSearchResult;
import org.example.easychat.Mapper.UserMapper;
import org.example.easychat.dto.*;
import org.example.easychat.service.UserService;
import org.example.easychat.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {


    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private final UserMapper userMapper;

    @Autowired
    private final UserService userService;

    public UserController(UserMapper userMapper, UserService userService) {
        this.userMapper = userMapper;
        this.userService = userService;
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/user")
    public ResponseBO<User> user(@RequestParam("id") String userId) {
        User user = userService.getUserById(userId);
        return ResponseBO.success("获取用户信息成功", user);
    }
    /**
     * 注册
     */
    @PostMapping("/register")
    public ResponseBO register(@RequestBody RegisterDto registerDto) {
        userService.register(registerDto);
        return ResponseBO.success("注册成功");
    }

    /**
     * 发送验证码
     */
    @PostMapping("/verifyCode/send")
    public ResponseBO sendVerifyCode(@RequestBody SendVerifyCodeDto dto) {
        userService.sendVerifyCode(dto.getEmail(), dto.getType());
        return ResponseBO.success("发送验证码成功");
    }
    /**
     * 验证验证码
     */
    @PostMapping("/verifyCode/validate")
    public ResponseBO validateVerifyCode(@RequestBody SendVerifyCodeDto dto){
        userService.validateVerifyCode(dto.getEmail(), dto.getVerifyCode());
        return ResponseBO.success("验证码验证成功");
    }
    /**
     * 验证用户名是否可用
     */
    @PostMapping("/username/validate")
    public ResponseBO validateUserName(@RequestBody String username){
        User user =userMapper.getUserByUsername(username);
        if (user != null){
            return ResponseBO.error("用户名已存在");}
        return ResponseBO.success("用户名可用");
    }
    /**
     * 根据用户名搜索用户
     */
    @GetMapping("/search")
    public ResponseBO<UserSearchResult> searchUser(@RequestParam("username") String username){
        UserSearchResult userSearchResult = userService.searchUser(username);
        return ResponseBO.success(userSearchResult);
    }

    /**
     * 获取好友列表
     */
    @GetMapping("/friends")
    public ResponseBO<List<friendsDto>> getFriendsList(@RequestParam("id") String userId){
        List<friendsDto> friendsList = userService.getFriendsList(userId);
        return ResponseBO.success(friendsList);
    }
    /**
     * 好友验证列表
     */
    @GetMapping("friendVerify")
    public ResponseBO<List<friendVerifyDto>> getFriendVerifyList(@RequestParam("id") String userId){
        List<friendVerifyDto> friendVerifyList = userService.getFriendVerifyList(userId);
        return ResponseBO.success(friendVerifyList);
    }
    /**
     * 编辑用户信息
     */
    @PostMapping("/user/edit")
    public ResponseBO<editDto> editUser(@RequestBody editDto editDto){
        userService.editUser(editDto);
        return ResponseBO.success(editDto);
    }
    /**
     * 更換用戶頭像
     */
    @PostMapping("/user/changeAvatar")
    public ResponseBO<Map<String, String>> changeAvatar(@RequestParam("id") String userId, @RequestParam("file") MultipartFile avatar) throws IOException {
        String avatarUrl = userService.updateUserAvatar(avatar, userId);
        Map<String, String> data = new HashMap<>();
        data.put("avatar", avatarUrl);
        return ResponseBO.success("修改成功", data);
    }
    /**
     * 更改用戶密碼
     */

    @PostMapping("/user/changePassword")
    public ApiResponseBO changePassword(@RequestBody ChangePasswordDto changePasswordDto){
        userService.changePassword(changePasswordDto.getUserId(), changePasswordDto.getCheckPassword(), changePasswordDto.getNewPassword());
        return ApiResponseBO.success(null);
    }


    /**
     * 验证用户密码是否正确
     */
    @PostMapping("/password/validate")
    public ResponseBO<Boolean> validatePassword(@RequestBody Map<String, String> payload) {
        String userId = payload.get("userId");
        String password = payload.get("password");

        if (userId == null || password == null) {
            return ResponseBO.error("用户ID和密码不能为空");
        }
        
        boolean isPasswordCorrect = userService.validatePassword(userId, password);
        if (isPasswordCorrect) {
            return ResponseBO.success("密码正确", true);
        } else {
            return ResponseBO.error("密码错误");
        }
    }
    /**
     * 增加用戶標簽
     */
    @PostMapping("/user/addTag")
    public ResponseBO<Map<String, String>> addTag(@RequestBody Map<String, String> payload) {
        String userId = payload.get("userId");
        String tag = payload.get("tag");
        Map<String, String> data =userService.addTag(userId, tag);
        return ResponseBO.success("添加标签成功", data);
    }
    /**
     *删除用户标签
     */
    @PostMapping("/user/removeTag")
    public ResponseBO<Map<String, String>> removeTag(@RequestBody Map<String, String> payload) {
        String userId = payload.get("userId");
        String tag = payload.get("tag");
        Map<String, String> data = userService.removeTag(userId, tag);
        return ResponseBO.success("删除标签成功", data);
    }
}
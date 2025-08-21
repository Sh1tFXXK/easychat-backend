package org.example.easychat.service;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.Entity.User;
import org.example.easychat.Entity.UserSearchResult;
import org.example.easychat.Mapper.UserMapper;
import org.example.easychat.dto.RegisterDto;
import org.example.easychat.dto.editDto;
import org.example.easychat.dto.friendVerifyDto;
import org.example.easychat.dto.friendsDto;
import org.example.easychat.utils.AliOSSUtil;
import org.example.easychat.utils.JwtUtil;
import org.example.easychat.utils.ValidationUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserService implements UserInterface{

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwt;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private AliOSSUtil aliOSSUtil;

    @Override
    public User getUserById(String userId) {
        User user = userMapper.getUserWithTagsById(userId);
        if(user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }
    @Override
    public void sendVerifyCode(String email, String type) {
        // 验证邮箱格式
        if(!email.matches( "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$")){
            throw new IllegalArgumentException("邮箱格式错误");
        }
        // 检查发送频率限制
        String rateLimitKey = "verify_code_limit:" + email;
        Boolean isLimited = redisTemplate.hasKey(rateLimitKey);

        if (isLimited) {
            // 获取剩余冷却时间
            Long ttl = redisTemplate.getExpire(rateLimitKey, TimeUnit.SECONDS);
            throw new IllegalArgumentException("发送过于频繁，请" + ttl + "秒后再试");
        }
        // 生成验证码
        String  verifyCode = jwt.generateCode();
        //根据不同的类型设置不同的验证码key
        String verifyCodeKey = "verify_code:" +  ":" + email;
        // 将验证码保存到Redis中，并设置过期时间
        redisTemplate.opsForValue().set(verifyCodeKey, verifyCode, 5 * 60, TimeUnit.SECONDS);
        // 设置验证码发送频率限制
        redisTemplate.opsForValue().set(rateLimitKey, true, 60, TimeUnit.SECONDS);

        // 创建邮件消息
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom( "3141479867@qq.com");
        message.setTo(email);
        message.setSubject("验证码");
        message.setText("您的验证码是：" + verifyCode);
        mailSender.send(message);
    }
    public void validateVerifyCode(String email, String code) {
        String verifyCodeKey = "verify_code:" +  ":" + email;
        // 获取Redis中的验证码
        Object redisCodeObj = redisTemplate.opsForValue().get(verifyCodeKey);

        if (redisCodeObj == null) {
            throw new RuntimeException("验证码不存在或已过期");
        }

        String redisCode = redisCodeObj.toString();
        if (!redisCode.equals(code)) {
            throw new RuntimeException("验证码错误");
        }
        // 验证码正确，删除Redis中的验证码
        redisTemplate.delete(verifyCodeKey);
    }

    @Override
    public void register(RegisterDto registerDto) {
        // 校验用户名
        if (registerDto.getUsername() == null || !ValidationUtils.isValidUsername(registerDto.getUsername())) {
            throw new IllegalArgumentException("用户名不符合规范：6-11位，字母开头，只能包含字母、数字、下划线，不能以下划线结尾");
        }

        // 校验昵称
        if (registerDto.getNickName() == null || !ValidationUtils.isValidNickName(registerDto.getNickName())) {
            throw new IllegalArgumentException("昵称不符合规范：1-11位");
        }

        // 校验密码
        if (registerDto.getPassword() == null || !ValidationUtils.isValidPassword(registerDto.getPassword())) {
            throw new IllegalArgumentException("密码不符合规范：8-16位，必须包含字母、数字、特殊字符中至少两种");
        }

        // 校验邮箱
        if (registerDto.getEmail() == null || !ValidationUtils.isValidEmail(registerDto.getEmail())) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }

        // 校验验证码
        if (registerDto.getVerifyCode() == null || !ValidationUtils.isValidVerifyCode(registerDto.getVerifyCode())) {
            throw new IllegalArgumentException("验证码格式不正确：必须为6位数字");
        }

        //检验用户名是否被占用
        if (userMapper.getUserByUsername(registerDto.getUsername()) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        //密码明文存储
        User user = new User();
        user.setId(generateId());
        //插入数据库
        BeanUtils.copyProperties(registerDto, user);
        user.setStatus(1);
        // 使用SimpleDateFormat格式化时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        user.setCreateTime(sdf.format(new Date()));
        userMapper.insertUser(user);

    }

    private long lastTimestamp = -1L;

    private long sequence = 0L;

    private String generateId() {

        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨");
        }

        long sequenceBits = 12L;
        if (timestamp == lastTimestamp) {
            long sequenceMask = ~(-1L << sequenceBits);
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;

        long nodeIdBits = 10L;
        long timestampShift = sequenceBits + nodeIdBits;
        long nodeId = 1L;
        return String.valueOf((timestamp << timestampShift)
                | nodeId
                | sequence);
    }
    /**
     * 阻塞到下一个毫秒直到时间前进
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
    @Override
    public UserSearchResult searchUser(String username) {
        UserSearchResult userSearchResult = new UserSearchResult();
        User user = userMapper.getUserByUsername(username);
        if (user == null) {
            log.info("用户不存在");
            return userSearchResult;
        }
        BeanUtils.copyProperties(user, userSearchResult);
        List<String> tags;
        tags=userMapper.getUserTags(user.getId());
        userSearchResult.setTags(tags);
        return userSearchResult;
    }

    @Override
    public List<friendsDto> getFriendsList(String userId) {
        // 1. 获取好友基础信息，MyBatis已通过@MapKey处理成Map
        Map<String, friendsDto> friendsMap = userMapper.getUserFriendsBaseInfo(userId);
        if (friendsMap == null || friendsMap.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 提取所有好友的ID
        List<String> friendUserIds = new ArrayList<>(friendsMap.keySet());

        // 3. 一次性查询所有好友的所有标签
        List<Map<String, Object>> tagsResult = userMapper.findTagsForUserIds(friendUserIds);

        // 4. 将标签聚合到Map中的好友对象上
        for (Map<String, Object> row : tagsResult) {
            String friendId = (String) row.get("user_id");
            String tag = (String) row.get("tag");

            friendsDto friendDto = friendsMap.get(friendId);
            if (friendDto != null) {
                if (friendDto.getFriendTags() == null) {
                    friendDto.setFriendTags(new ArrayList<>());
                }
                friendDto.getFriendTags().add(tag);
            }
        }

        // 5. (BUG修复) 将当前用户的ID设置到每个好友DTO中
        for (friendsDto friendDto : friendsMap.values()) {
            friendDto.setUserId(userId);
        }

        // 6. 返回Map中的所有值（即所有好友的DTO）
        return new ArrayList<>(friendsMap.values());
    }

    @Override
    public List<friendVerifyDto> getFriendVerifyList(String userId) {
        // 直接通过一条SQL查询获取所有好友验证信息
        return userMapper.getFriendVerifications(userId);
    }

    public void editUser(editDto editDto) {
        User user = userMapper.getUserById(editDto.getUserId());
        if (user == null) {
            log.error("用户不存在");
        }
        BeanUtils.copyProperties(editDto, user);
        // 使用SimpleDateFormat格式化时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        user.setUpdateTime(sdf.format(new Date()));
        userMapper.updateById(user);
    }
    @Override
    public String updateUserAvatar(MultipartFile avatar, String userId) throws IOException {
        User user = userMapper.getUserById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 上传头像到OSS并获取URL
        String avatarUrl = aliOSSUtil.uploadImage(avatar);
        
        // 更新用户头像URL
        user.setAvatar(avatarUrl);
        
        // 使用Java 8的时间API格式化时间
        user.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        userMapper.updateById(user);
        
        return avatarUrl;
    }

    @Override
    public void changePassword(String userId, String checkPassword, String newPassword) {
        User user = userMapper.getUserById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证旧密码是否正确（明文比较）
        if (!newPassword.equals(checkPassword)) {
            throw new IllegalArgumentException("兩次密碼不一致");
        }
        
        // 更新为新密码（明文存储）
        user.setPassword(newPassword);
        user.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        userMapper.updateById(user);
    }

    @Override
    public boolean validatePassword(String userId, String password) {
        User user = userMapper.getUserById(userId);
        if (user == null) {
            log.error("用户不存在");
            return false;
        }

        // 确保密码字段不为空
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            log.error("用户密码为空");
            return false;
        }
        
        // 明文密码比较
        return user.getPassword().equals(password);
    }
    @Override
    public Map<String, String> addTag(String userId, String tag) {
        User user = userMapper.getUserById(userId);
        if (user == null) {
            log.error("用户不存在");
            return null;
        }
        //2. 验证标签是否已存在
        if (userMapper.getUserTags(userId).contains(tag)) {
            log.error("标签已存在");
        }
        userMapper.addTag(userId, tag);
        Map<String, String> result = new HashMap<>();
        result.put("userId", userId);
        result.put("tag", tag);
        return result;
    }

    public Map<String, String> removeTag(String userId, String tag) {
        User user = userMapper.getUserById(userId);
        if (user == null) {
            log.error("用户不存在");
            return null;
        }
        //2. 验证标签是否已存在
        if (!userMapper.getUserTags(userId).contains(tag)) {
            log.error("标签不存在");
        }
        userMapper.removeTag(userId, tag);
        Map<String, String> result = new HashMap<>();
        result.put("userId", userId);
        result.put("tagName", tag);
        return result;
    }
}
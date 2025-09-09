package org.example.easychat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.easychat.BO.LoginBO;
import org.example.easychat.Entity.User;
import org.example.easychat.Mapper.UserMapper;
import org.example.easychat.dto.authDto;
import org.example.easychat.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AuthService implements AuthInterface{

    @Autowired
    private  UserMapper userMapper;

    @Autowired
    private JwtUtil jwt;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    @Override
    public LoginBO login(authDto authDto) {
        // 检查参数是否为空
        if (authDto == null || authDto.getUsername() == null || authDto.getPassword() == null) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }
        // 检验用户名格式 (6-11位，字母开头)
        if (!authDto.getUsername().matches("^[a-zA-Z][a-zA-Z0-9]{5,10}$"))
            throw new IllegalArgumentException("用户名格式错误");

        // 验证密码格式 (8-16位，包含至少两种字符类型)
        String p = authDto.getPassword();
        if (p.length() < 8 || p.length() > 16 ||
                ((p.matches(".*[a-zA-Z].*") ? 1 : 0) + (p.matches(".*[0-9].*") ? 1 : 0) +
                        (p.matches(".*[^a-zA-Z0-9].*") ? 1 : 0)) < 2)
            throw new IllegalArgumentException("密码格式错误");

        //检查用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", authDto.getUsername())
                .eq("password", authDto.getPassword());
        User user = userMapper.selectOne(queryWrapper);

        if (user == null){
            throw new IllegalArgumentException("用户或密码错误");
        }
        
        // 生成token
        String token = jwt.generateToken(user.getId());
        
        // 登录成功后存储token
        // userToken:<userId> -> <token>
        redisTemplate.opsForValue().set("userToken:" + user.getId(), token, 7, TimeUnit.DAYS);
        
        // 更新用户状态
        user.setStatus(1);
        userMapper.updateById(user);
        
        //通知好友上线
        
        // 直接返回用户ID和token
        return new LoginBO(user.getId(), token);
    }

    @Override
    public void logout(String token) {
        if(token == null || token.isEmpty()) {
            throw new IllegalArgumentException("用户未登录");
        }

        // 先取 userId
        String userId = (String) redisTemplate.opsForValue().get("token:" + token);
        
        // 删除 token
        if (userId != null && !userId.isEmpty()) {
            redisTemplate.delete("userToken:" + userId);
        }
        
        //更新用戶离线状态
        if (userId != null && !userId.isEmpty()) {
            userMapper.updateUserStatus(0, userId);
        }
        //通过WebSocket通知好友用户离线

    }
}

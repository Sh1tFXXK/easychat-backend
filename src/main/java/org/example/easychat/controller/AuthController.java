package org.example.easychat.controller;

import org.example.easychat.BO.LoginBO;
import org.example.easychat.BO.ResponseBO;
import org.example.easychat.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.example.easychat.dto.authDto;


/**
 * AuthController
 * 实现登录和登出功能
 * @author 31414
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;


    /**
     * 登录
     *
     * @param authDto
     * @return
     */
    @PostMapping("/login")
    public ResponseBO login(@RequestBody authDto authDto) {
        LoginBO loginBO = authService.login(authDto);
        return ResponseBO.success("Login successful", loginBO);
    }

    /**
     * 登出
     *
     * @return
     * @throws Exception
     */
    @PostMapping("/logout")
    public ResponseBO logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            // 从请求头获取token，格式为"Bearer xxx"
            String token = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            }

            authService.logout(token);
            return ResponseBO.success("登出成功", null);
        } catch (IllegalArgumentException e) {
            return ResponseBO.error(e.getMessage());
        }
    }
}

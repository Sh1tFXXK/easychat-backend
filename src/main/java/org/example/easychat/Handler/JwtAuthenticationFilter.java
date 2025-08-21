package org.example.easychat.Handler;

import org.example.easychat.Entity.User;
import org.example.easychat.Mapper.UserMapper;
import org.example.easychat.utils.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.redis.core.RedisTemplate;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 从请求头获取Token
        String token = extractTokenFromRequest(request);

        if (token != null && validateToken(token)) {
            // 2. 验证Token并解析出用户ID
            String userId = getUserIdFromToken(token);

            if (userId != null) {
                // 3. 加载用户信息 (从数据库查询)
                User user = userMapper.getUserById(userId);
                
                if (user != null) {
                    // 4. 创建认证对象，并存入Spring Security的上下文中
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user, null, new ArrayList<>());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
    
    /**
     * 从请求中提取JWT token
     * @param request HTTP请求
     * @return 提取到的token，如果不存在则返回null
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    /**
     * 验证token是否有效
     * @param token JWT token
     * @return token是否有效
     */
    private boolean validateToken(String token) {
        try {
            // 先检查JWT本身是否有效
            if (jwtUtil.validateToken(token)) {
                // 再检查Redis中是否存在该token，保持与ChatSocketIOHandler的一致性
                return Boolean.TRUE.equals(redisTemplate.hasKey("token:" + token));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 从token中获取用户ID
     * @param token JWT token
     * @return 用户ID
     */
    private String getUserIdFromToken(String token) {
        try {
            // 从Redis中获取与token关联的用户ID，保持与ChatSocketIOHandler的一致性
            Object userId = redisTemplate.opsForValue().get("token:" + token);
            return userId != null ? userId.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

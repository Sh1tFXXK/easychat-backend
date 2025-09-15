package org.example.easychat.Config;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import org.example.easychat.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SocketIOConfig {

    @Autowired
    private JwtUtil jwtUtil;

    @Bean
    public SocketIOServer socketIOServer() {
        com.corundumstudio.socketio.Configuration config =
                new com.corundumstudio.socketio.Configuration();

        config.setHostname("localhost");
        config.setPort(8082); // Change this if port 8082 is in use
        config.setOrigin("*");

        // 设置心跳间隔
        config.setPingInterval(25000);
        config.setPingTimeout(5000);
        
        // 设置最大帧长度
        config.setMaxFramePayloadLength(1024 * 1024);
        config.setMaxHttpContentLength(1024 * 1024);
        
        // 设置认证逻辑
        config.setAuthorizationListener(data -> {
            try {
                String token = data.getSingleUrlParam("token");
                System.out.println("[Socket.IO] Handshake data: " + data.getHttpHeaders());
                System.out.println("[Socket.IO] URL: " + data.getUrl());
                System.out.println("[Socket.IO] Token received: " + (token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null"));
                
                if (token == null || token.isEmpty()) {
                    System.out.println("[Socket.IO] No token provided in authorization");
                    return false;
                }
                
                // URL解码处理
                try {
                    token = java.net.URLDecoder.decode(token, "UTF-8");
                } catch (Exception e) {
                    System.out.println("[Socket.IO] Token URL decode failed: " + e.getMessage());
                }
                
                // 移除Bearer前缀（如果存在）
                if (token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }
                
                System.out.println("[Socket.IO] Processing token (after decode/prefix removal): " + token.substring(0, Math.min(20, token.length())) + "...");
                
                // 验证JWT token
                String userId = jwtUtil.getUserIdFromToken(token);
                if (userId != null && !userId.isEmpty()) {
                    System.out.println("[Socket.IO] Authorization successful for user: " + userId);
                    return true;
                } else {
                    System.out.println("[Socket.IO] Invalid token or user ID extraction failed during authorization");
                    return false;
                }
            } catch (Exception e) {
                System.out.println("[Socket.IO] Authorization error: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });

        return new SocketIOServer(config);
    }

    @Bean
    public SpringAnnotationScanner springAnnotationScanner(SocketIOServer socketIOServer) {
        return new SpringAnnotationScanner(socketIOServer);
    }
}
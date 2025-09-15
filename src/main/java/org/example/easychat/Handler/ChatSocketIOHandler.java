package org.example.easychat.Handler;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.easychat.Entity.*;
import org.example.easychat.Mapper.ChatMapper;
import org.example.easychat.Mapper.GroupChatMapper;
import org.example.easychat.Mapper.UserMapper;
import org.example.easychat.dto.AnswerCallRequest;
import org.example.easychat.dto.RejectCallRequest;
import org.example.easychat.dto.friendVerifyDto;
import org.example.easychat.utils.JwtUtil;
import org.example.easychat.utils.ValidationUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSocketIOHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ChatSocketIOHandler.class);

    @Autowired
    private SocketIOServer socketIOServer;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ChatMapper chatMapper;

    @Autowired
    private GroupChatMapper groupChatMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 用户会话映射
    private static final ConcurrentHashMap<String, SocketIOClient> sessions = new ConcurrentHashMap<>();

    // 在线用户列表 (userId -> status)
    private static final ConcurrentHashMap<String, Integer> onlineUsers = new ConcurrentHashMap<>();

    /**
     * 用户连接时处理
     */
    @OnConnect
    public void onConnect(SocketIOClient client) {
        log.info("Socket.IO连接已建立: {}", client.getSessionId());
        
        // 从连接参数中获取token
        String raw = client.getHandshakeData().getSingleUrlParam("token");
        log.info("Handshake data: {}", client.getHandshakeData());
        log.info("Query params: {}", client.getHandshakeData().getUrlParams());
        log.info("Full URL: {}", client.getHandshakeData().getUrl());

        if (raw == null || raw.isEmpty()) {
            log.warn("连接中未提供token，断开连接: {}", client.getSessionId());
            client.sendEvent("error", "Missing authentication token");
            client.disconnect();
            return;
        }

        String token;
        try {
            token = URLDecoder.decode(raw, StandardCharsets.UTF_8);
            if(token.startsWith("Bearer ")){
                token = token.substring(7);
            }
            log.info("尝试验证token（已解码/去前缀）：{}...", token.length() > 20 ? token.substring(0, 20) + "..." : token);

            if (validateSocketToken(token)) {
                String userId = getUserIdFromToken(token);
                if (userId != null && !userId.isEmpty()) {
                    client.set("userId", userId);
                    client.set("authenticated", true);
                    log.info("用户认证成功: userId={}", userId);
                    // 发送认证成功确认
                    client.sendEvent("authenticated", Map.of("userId", userId, "status", "success"));
                } else {
                    log.warn("从token中无法解析出有效的用户ID，断开连接: {}", client.getSessionId());
                    client.sendEvent("error", "Invalid user ID in token");
                    client.disconnect();
                    return;
                }
            } else {
                log.warn("Token验证失败，断开连接: {}", client.getSessionId());
                client.sendEvent("error", "Token validation failed");
                client.disconnect();
                return;
            }
        } catch (Exception e) {
            log.error("处理连接认证时发生异常，断开连接: {}", client.getSessionId(), e);
            client.sendEvent("error", "Authentication error");
            client.disconnect();
            return;
        }
    }

    /**
     * 用户断开连接时处理
     */
    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        String sessionId = client.getSessionId().toString();
        log.info("Socket.IO连接已关闭: {}", sessionId);

        // 获取断开连接的用户ID
        String userId = client.get("userId");
        if (userId != null) {
            log.info("用户 {} 断开连接，当前在线用户数: {}", userId, sessions.size());
            
            // 清理用户的通话会话
            cleanupUserCalls(userId);
            
            // 从在线用户列表中移除
            onlineUsers.remove(userId);
            sessions.remove(userId);
            log.info("用户 {} 已从会话映射中移除，剩余在线用户数: {}", userId, sessions.size());
            log.info("当前在线用户列表: {}", sessions.keySet());
            // 广播更新后的在线用户列表
            broadcastOnlineUsers();
        } else {
            log.warn("断开连接的客户端没有关联的用户ID: {}", sessionId);
        }

        removeSession(client);
    }

    /**
     * 用户上线时处理
     * @param client
     * @param userId
     * @param status
     */
    @OnEvent("online")
    public void onUserOnline(String userId, Integer status, SocketIOClient client) {
        try {
            log.info("收到上线事件: userId={}, status={}, sessionId={}", userId, status, client.getSessionId());
            
            // 直接从已认证的客户端获取用户ID，不依赖前端传递的参数
            String authenticatedUserId = client.get("userId");
            Boolean isAuthenticated = client.get("authenticated");
            log.info("客户端认证状态: authenticatedUserId={}, isAuthenticated={}, requestedUserId={}", 
                    authenticatedUserId, isAuthenticated, userId);
            
            if (authenticatedUserId == null || authenticatedUserId.isEmpty()) {
                log.warn("用户未认证，但不强制断开连接: sessionId={}", client.getSessionId());
                client.sendEvent("error", "Authentication required for online status");
                return;
            }

            // 使用已认证的用户ID，忽略前端传递的可能为空的userId参数
            String actualUserId = authenticatedUserId;
            log.info("使用已认证的用户ID: {}", actualUserId);

            log.info("用户上线验证通过: userId={}, status={}", actualUserId, status);

            // 避免重复处理同一用户的上线事件
            if (onlineUsers.containsKey(actualUserId) && sessions.containsKey(actualUserId)) {
                log.info("用户 {} 已在线，忽略重复上线事件", actualUserId);
                client.sendEvent("onlineConfirmed", "already_online");
                return;
            }

            // 1. 将用户ID添加到在线用户列表中
            onlineUsers.put(actualUserId, status);
            sessions.put(actualUserId, client);
            
            log.info("用户 {} 已添加到会话映射，当前在线用户数: {}", actualUserId, sessions.size());
            log.info("当前所有在线用户: {}", sessions.keySet());

            // 2. 广播更新后的在线用户列表给所有连接的客户端
            broadcastOnlineUsers();

            // 3. 通知所有用户该用户已上线
            socketIOServer.getBroadcastOperations().sendEvent("userOnline", actualUserId);

            // 4. 向当前用户发送确认消息
            client.sendEvent("onlineConfirmed", "success");

            // 5.获取在线用户列表
            getCurrentOnlineUsers();
        } catch (Exception e) {
            log.error("处理用户上线时出错", e);
        }
    }

    private void getCurrentOnlineUsers() {
        //todo 获取在线用户列表
    }

    /**
     * 心跳检测事件处理
     */
    @OnEvent("ping")
    public void onPing(SocketIOClient client) {
        // 响应心跳
        client.sendEvent("pong", "heartbeat");
    }

    @OnEvent("offline")
    public void onUserOffline( String userId) {
        try {
            log.info("用户下线: userId={}", userId);

            // 1. 从在线用户列表中移除该用户
            onlineUsers.remove(userId);
            sessions.remove(userId);

            // 2. 广播更新后的在线用户列表
            broadcastOnlineUsers();

            // 3. 通知所有用户该用户已下线
            socketIOServer.getBroadcastOperations().sendEvent("userOffline", userId);

        } catch (Exception e) {
            log.error("处理用户下线时出错", e);
        }
    }

    /**
     * 发送消息处理
     * @param message
     * @param ackRequest
     */
    @OnEvent("sendMsg")
    public void onSendMessage( Message message, AckRequest ackRequest) {
        try {
            log.info("收到消息: {}", message);

            // 检查发送者和接收者是否为好友
            User sender = userMapper.getUserById(message.getSenderId());
            User receiver = userMapper.getUserById(message.getReceiverId());

            if (sender == null || receiver == null) {
                // 发送错误响应
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData(null, "error");
                }
                return;
            }

            List<String> friendIds = userMapper.getUserFriendsIds(sender.getId());
            if (!friendIds.contains(receiver.getId())) {
                // 发送错误响应
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData(null, "notFriend");
                }
                return;
            }

            // 保存消息到数据库
            ChatHistory chatHistory = new ChatHistory();
            BeanUtils.copyProperties(message, chatHistory);
            String id = jwtUtil.generateCode();
            chatHistory.setId(id);
            chatMapper.insert(chatHistory);

            // 构造响应信息
            chatHistory.setHasRead(0);

            // 发送成功响应给发送者
            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData(chatHistory, "");
            }

            // 推送消息给接收者
            SocketIOClient receiverClient = sessions.get(receiver.getId());
            if (receiverClient != null && receiverClient.isChannelOpen()) {
                receiverClient.sendEvent("receiveMsg", chatHistory);
            } else {
                // 如果接收者离线，发送离线通知
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData(chatHistory, "offline");
                }
            }

        } catch (Exception e) {
            log.error("处理发送消息时出错", e);
            // 发送错误响应
            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData("error");
            }
        }
    }

    /**
     * 从会话映射中移除会话
     */
    private void removeSession(SocketIOClient client) {
        // 找到对应的用户ID并从在线列表中移除
        String userIdToRemove = null;
        for (Map.Entry<String, SocketIOClient> entry : sessions.entrySet()) {
            if (entry.getValue().equals(client)) {
                userIdToRemove = entry.getKey();
                break;
            }
        }

        if (userIdToRemove != null) {
            onlineUsers.remove(userIdToRemove);
            sessions.remove(userIdToRemove);
        }

        // 广播更新后的在线用户列表
        try {
            broadcastOnlineUsers();
        } catch (Exception e) {
            log.error("广播在线用户时出错", e);
        }
    }

    //存储当前通话状态
    private final  Map<String, CallSession> activeCalls = new ConcurrentHashMap<>();

    /**
     * 清理用户的通话会话
     */
    private void cleanupUserCalls(String userId) {
        activeCalls.entrySet().removeIf(entry -> {
            CallSession session = entry.getValue();
            boolean shouldRemove = session.getCallerId().equals(userId) || 
                                 session.getReceiverId().equals(userId) ||
                                 (session.isGroupCall() && session.getActiveParticipants().contains(userId));
            
            if (shouldRemove) {
                log.info("清理用户 {} 的通话会话: {}", userId, entry.getKey());
                
                // 通知其他参与者通话结束
                if (session.isGroupCall()) {
                    broadcastToCallParticipants(session, "callEnded", 
                        Map.of("callId", entry.getKey(), "reason", "participant_disconnected"), userId);
                } else {
                    // 一对一通话，通知对方
                    String otherUserId = session.getCallerId().equals(userId) ? 
                        session.getReceiverId() : session.getCallerId();
                    SocketIOClient otherClient = findClientById(otherUserId);
                    if (otherClient != null) {
                        otherClient.sendEvent("callEnded", 
                            Map.of("callId", entry.getKey(), "reason", "peer_disconnected"));
                    }
                }
            }
            
            return shouldRemove;
        });
    }

    /**
     * 根据用户ID查找客户端
     */
    private SocketIOClient findClientById(String userId) {
        Collection<SocketIOClient> clients = socketIOServer.getAllClients();
        log.info("查找用户 {} 的客户端连接，当前总连接数: {}", userId, clients.size());
        
        for (SocketIOClient client : clients) {
            String clientUserId = client.get("userId");
            log.debug("检查客户端 {} 的用户ID: {}", client.getSessionId(), clientUserId);
            if (userId != null && userId.equals(clientUserId)) {
                log.info("找到用户 {} 的客户端连接: {}", userId, client.getSessionId());
                return client;
            }
        }
        
        log.warn("未找到用户 {} 的客户端连接", userId);
        
        // 打印所有在线用户的详细信息
        log.info("当前所有在线客户端:");
        for (SocketIOClient client : clients) {
            String clientUserId = client.get("userId");
            log.info("  - 会话ID: {}, 用户ID: {}", client.getSessionId(), clientUserId);
        }
        
        return null;
    }

    /**
     * 向通话参与者广播消息
     */
    private void broadcastToCallParticipants(CallSession session, String eventName,Map<String, Object> data, String excludeUserId) {
        for (String participantId : session.getParticipants()) {
            if (!participantId.equals(excludeUserId)) {
                SocketIOClient participantClient = findClientById(participantId);
                if (participantClient != null) {
                    participantClient.sendEvent(eventName, data);
                }
            }
        }
    }

    /**
     * 广播在线用户列表给所有连接的客户端
     */
    private void broadcastOnlineUsers() {
        try {
            // 构造在线用户列表
            Map<String, Integer> onlineUsersMap = new ConcurrentHashMap<>(onlineUsers);

            // 广播给所有连接的客户端
            socketIOServer.getBroadcastOperations().sendEvent("onlineUsers", onlineUsersMap);

        } catch (Exception e) {
            log.error("广播在线用户时出错", e);
        }
    }

    /**
     * 获取用户的Socket客户端
     * @param userId 用户ID
     * @return SocketIOClient 客户端实例
     */
    public SocketIOClient getUserClient(String userId) {
        return sessions.get(userId);
    }

    /**
     * 验证token是否有效
     * @param token JWT token
     * @return token是否有效
     */
    private boolean validateSocketToken(String token) {
        try {
            if (!jwtUtil.validateToken(token)) {
                log.warn("JWT验证失败或已过期，exp={}", jwtUtil.getExpirationDateFromToken(token));
                return false;
            }
            // 统一：仅校验 userToken:<userId> -> <token>
            String userId = jwtUtil.getUserIdFromToken(token);
            String cachedToken = stringRedisTemplate.opsForValue().get("userToken:" + userId);
            boolean ok = token.equals(cachedToken);
            if (!ok) {
                log.warn("Redis 未命中 userToken 映射，userId={}", userId);
            }
            return ok;
        } catch (Exception e) {
            log.error("验证token时出错", e);
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
            String userId = jwtUtil.getUserIdFromToken( token);
            log.info("从token中解析出userId: {}", userId);
            return userId;
        } catch (Exception e) {
            log.error("从token解析用户ID时出错", e);
            return null;
        }
    }

}
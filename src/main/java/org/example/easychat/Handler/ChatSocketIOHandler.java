package org.example.easychat.Handler;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import lombok.extern.slf4j.Slf4j;
import org.example.easychat.Entity.*;
import org.example.easychat.Mapper.ChatMapper;
import org.example.easychat.Mapper.GroupChatMapper;
import org.example.easychat.Mapper.UserMapper;
import org.example.easychat.dto.AnswerCallRequest;
import org.example.easychat.dto.RejectCallRequest;
import org.example.easychat.dto.friendVerifyDto;
import org.example.easychat.utils.JwtUtil;
import org.example.easychat.utils.ValidationUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson.JSON;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ChatSocketIOHandler {

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

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    // 用户会话映射 (userId -> client)
    private static final ConcurrentHashMap<String, SocketIOClient> sessions = new ConcurrentHashMap<>();

    // 在线用户列表 (userId -> status)
    private static final ConcurrentHashMap<String, Integer> onlineUsers = new ConcurrentHashMap<>();

    // 存储当前通话状态
    private final Map<String, CallSession> activeCalls = new ConcurrentHashMap<>();

    @OnConnect
    public void onConnect(SocketIOClient client) {
        log.info("Socket.IO连接已建立: {}", client.getSessionId());

        String raw = client.getHandshakeData().getSingleUrlParam("token");
        if (raw == null || raw.isEmpty()) {
            log.warn("连接中未提供token，断开连接: {}", client.getSessionId());
            client.sendEvent("error", "Missing authentication token");
            client.disconnect();
            return;
        }

        try {
            String token = URLDecoder.decode(raw, StandardCharsets.UTF_8);
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            log.info("尝试验证token（已解码/去前缀）：{}...", token.length() > 20 ? token.substring(0, 20) + "..." : token);

            if (!validateSocketToken(token)) {
                log.warn("Token验证失败，断开连接: {}", client.getSessionId());
                client.sendEvent("error", "Token validation failed");
                client.disconnect();
                return;
            }

            String userId = getUserIdFromToken(token);
            if (userId == null || userId.isEmpty()) {
                log.warn("从token中无法解析出有效的用户ID，断开连接: {}", client.getSessionId());
                client.sendEvent("error", "Invalid user ID in token");
                client.disconnect();
                return;
            }

            client.set("userId", userId);
            client.set("authenticated", true);

            // 单用户单连接：如已存在旧连接则断开并替换
            SocketIOClient old = sessions.put(userId, client);
            if (old != null && old.isChannelOpen()) {
                try { old.disconnect(); } catch (Exception ignored) {}
            }
            onlineUsers.put(userId, 1);

            // 在线状态缓存（5分钟心跳续期）
            try {
                stringRedisTemplate.opsForValue().set("user:online:" + userId, String.valueOf(System.currentTimeMillis()));
                stringRedisTemplate.expire("user:online:" + userId, java.time.Duration.ofMinutes(5));
            } catch (Exception e) {
                log.warn("缓存在线状态失败: userId={}, error={}", userId, e.getMessage());
            }

            // 记录会话token用于服务端校验
            try {
                stringRedisTemplate.opsForValue().set("user:session:" + userId, token);
                stringRedisTemplate.expire("user:session:" + userId, java.time.Duration.ofHours(2));
            } catch (Exception e) {
                log.warn("缓存用户会话失败: userId={}, error={}", userId, e.getMessage());
            }

            log.info("用户认证成功: userId={}", userId);
            client.sendEvent("authenticated", Map.of("userId", userId, "status", "success"));
            broadcastOnlineUsers();
        } catch (Exception e) {
            log.error("处理连接认证时发生异常，断开连接: {}", client.getSessionId(), e);
            client.sendEvent("error", "Authentication error");
            client.disconnect();
        }
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        String sessionId = client.getSessionId().toString();
        log.info("Socket.IO连接已关闭: {}", sessionId);

        String userId = client.get("userId");
        if (userId != null) {
            log.info("用户 {} 断开连接，当前在线用户数: {}", userId, sessions.size());

            try {
                stringRedisTemplate.delete("user:online:" + userId);
                stringRedisTemplate.opsForSet().remove("online:users", userId);
            } catch (Exception e) {
                log.warn("清理在线状态缓存失败: userId={}, error={}", userId, e.getMessage());
            }

            cleanupUserCalls(userId);
            onlineUsers.remove(userId);
            sessions.remove(userId);
            broadcastOnlineUsers();
        } else {
            log.warn("断开连接的客户端没有关联的用户ID: {}", sessionId);
        }

        removeSession(client);
    }

    @OnEvent("online")
    public void onUserOnline(String userId, Integer status, SocketIOClient client) {
        try {
            log.info("收到上线事件: userId={}, status={}, sessionId={}", userId, status, client.getSessionId());
            String authenticatedUserId = client.get("userId");
            Boolean isAuthenticated = client.get("authenticated");
            log.info("客户端认证状态: authenticatedUserId={}, isAuthenticated={}, requestedUserId={}", authenticatedUserId, isAuthenticated, userId);

            if (authenticatedUserId == null || authenticatedUserId.isEmpty()) {
                log.warn("用户未认证，但不强制断开连接: sessionId={}", client.getSessionId());
                client.sendEvent("error", "Authentication required for online status");
                return;
            }

            String actualUserId = authenticatedUserId;
            log.info("使用已认证的用户ID: {}", actualUserId);

            if (onlineUsers.containsKey(actualUserId) && sessions.containsKey(actualUserId)) {
                log.info("用户 {} 已在线，忽略重复上线事件", actualUserId);
                client.sendEvent("onlineConfirmed", "already_online");
                return;
            }

            onlineUsers.put(actualUserId, status == null ? 1 : status);
            sessions.put(actualUserId, client);

            broadcastOnlineUsers();
            socketIOServer.getBroadcastOperations().sendEvent("userOnline", actualUserId);
            client.sendEvent("onlineConfirmed", "success");

            getCurrentOnlineUsers();
        } catch (Exception e) {
            log.error("处理用户上线时出错", e);
        }
    }

    @OnEvent("ping")
    public void onPing(SocketIOClient client) {
        client.sendEvent("pong", "heartbeat");
    }

    @OnEvent("offline")
    public void onUserOffline(String userId) {
        try {
            log.info("用户下线: userId={}", userId);
            onlineUsers.remove(userId);
            sessions.remove(userId);
            broadcastOnlineUsers();
            socketIOServer.getBroadcastOperations().sendEvent("userOffline", userId);
        } catch (Exception e) {
            log.error("处理用户下线时出错", e);
        }
    }

    @OnEvent("sendMsg")
    public void onSendMessage(Message message, AckRequest ackRequest) {
        try {
            log.info("收到消息: {}", message);

            User sender = userMapper.getUserById(message.getSenderId());
            User receiver = userMapper.getUserById(message.getReceiverId());
            if (sender == null || receiver == null) {
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData(null, "error");
                }
                return;
            }

            List<String> friendIds = userMapper.getUserFriendsIds(sender.getId());
            if (friendIds == null || !friendIds.contains(receiver.getId())) {
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData(null, "notFriend");
                }
                return;
            }

            ChatHistory chatHistory = new ChatHistory();
            BeanUtils.copyProperties(message, chatHistory);
            chatHistory.setId(jwtUtil.generateCode());
            chatHistory.setHasRead(0);

            chatMapper.insert(chatHistory);

            try {
                String latestKey = "chat:latest:" + chatHistory.getSessionId();
                stringRedisTemplate.opsForValue().set(latestKey, JSON.toJSONString(chatHistory));
                stringRedisTemplate.expire(latestKey, java.time.Duration.ofHours(1));
                String unreadKey = "chat:unread:" + chatHistory.getReceiverId();
                stringRedisTemplate.opsForHash().increment(unreadKey, chatHistory.getSessionId(), 1);
                stringRedisTemplate.expire(unreadKey, java.time.Duration.ofHours(24));
            } catch (Exception e) {
                log.warn("未读与最新消息缓存写入失败: messageId={}, error={}", chatHistory.getId(), e.getMessage());
            }

            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData(chatHistory, "");
            }

            SocketIOClient receiverClient = sessions.get(receiver.getId());
            if (receiverClient != null && receiverClient.isChannelOpen()) {
                receiverClient.sendEvent("receiveMsg", chatHistory);
            } else {
                try {
                    String unreadKey = "chat:unread:" + receiver.getId();
                    stringRedisTemplate.opsForHash().increment(unreadKey, chatHistory.getSessionId(), 1);
                    stringRedisTemplate.expire(unreadKey, java.time.Duration.ofHours(24));

                    String latestKey = "chat:latest:" + chatHistory.getSessionId();
                    stringRedisTemplate.opsForValue().set(latestKey, JSON.toJSONString(chatHistory));
                    stringRedisTemplate.expire(latestKey, java.time.Duration.ofHours(1));
                } catch (Exception e) {
                    log.warn("离线消息缓存失败: sessionId={}, receiverId={}, error={}", chatHistory.getSessionId(), receiver.getId(), e.getMessage());
                }

                try {
                    if (rabbitTemplate != null) {
                        rabbitTemplate.convertAndSend("attention.notification.exchange", "attention.notification", JSON.toJSONString(chatHistory));
                    }
                } catch (Exception e) {
                    log.warn("RabbitMQ通知失败: messageId={}, error={}", chatHistory.getId(), e.getMessage());
                }

                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData(chatHistory, "offline");
                }
            }
        } catch (Exception e) {
            log.error("处理发送消息时出错", e);
            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData("error");
            }
        }
    }

    // WebSocket事件：批量标记会话为已读
    @OnEvent("markReadBatch")
    public void onMarkReadBatch(String sessionId, String userId, AckRequest ackRequest) {
        try {
            if (sessionId == null || userId == null) {
                if (ackRequest.isAckRequested()) ackRequest.sendAckData("invalid_params");
                return;
            }
            int updated = chatMapper.batchMarkAsRead(sessionId, userId);
            try {
                String unreadKey = "chat:unread:" + userId;
                stringRedisTemplate.opsForHash().delete(unreadKey, sessionId);
            } catch (Exception e) {
                log.warn("清理未读缓存失败: sessionId={}, userId={}, error={}", sessionId, userId, e.getMessage());
            }
            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData(Map.of("updated", updated));
            }
        } catch (Exception e) {
            log.error("批量标记已读失败: sessionId={}, userId={}", sessionId, userId, e);
            if (ackRequest.isAckRequested()) ackRequest.sendAckData("error");
        }
    }

    // WebSocket事件：游标分页获取聊天历史
    @OnEvent("historyCursor")
    public void onHistoryCursor(String sessionId, String lastId, Integer limit, AckRequest ackRequest) {
        try {
            if (sessionId == null || sessionId.isEmpty()) {
                if (ackRequest.isAckRequested()) ackRequest.sendAckData("invalid_params");
                return;
            }
            int pageSize = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
            List<ChatHistory> list = chatMapper.getChatHistoryCursor(sessionId, lastId, pageSize);
            String nextLastId = (list != null && !list.isEmpty()) ? list.get(list.size() - 1).getId() : lastId;
            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData(Map.of(
                    "records", list,
                    "nextLastId", nextLastId,
                    "pageSize", pageSize
                ));
            }
        } catch (Exception e) {
            log.error("游标分页查询失败: sessionId={}, lastId={}, limit={}", sessionId, lastId, limit, e);
            if (ackRequest.isAckRequested()) ackRequest.sendAckData("error");
        }
    }

    private void removeSession(SocketIOClient client) {
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
        try {
            broadcastOnlineUsers();
        } catch (Exception e) {
            log.error("广播在线用户时出错", e);
        }
    }

    private void cleanupUserCalls(String userId) {
        activeCalls.entrySet().removeIf(entry -> {
            CallSession session = entry.getValue();
            boolean shouldRemove = session.getCallerId().equals(userId)
                || session.getReceiverId().equals(userId)
                || (session.isGroupCall() && session.getActiveParticipants().contains(userId));

            if (shouldRemove) {
                log.info("清理用户 {} 的通话会话: {}", userId, entry.getKey());
                if (session.isGroupCall()) {
                    broadcastToCallParticipants(session, "callEnded",
                        Map.of("callId", entry.getKey(), "reason", "participant_disconnected"), userId);
                } else {
                    String otherUserId = session.getCallerId().equals(userId) ? session.getReceiverId() : session.getCallerId();
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

    private SocketIOClient findClientById(String userId) {
        Collection<SocketIOClient> clients = socketIOServer.getAllClients();
        for (SocketIOClient client : clients) {
            String clientUserId = client.get("userId");
            if (userId != null && userId.equals(clientUserId)) {
                return client;
            }
        }
        return null;
    }

    private void broadcastOnlineUsers() {
        try {
            Map<String, Integer> onlineUsersMap = new ConcurrentHashMap<>(onlineUsers);
            socketIOServer.getBroadcastOperations().sendEvent("onlineUsers", onlineUsersMap);
        } catch (Exception e) {
            log.error("广播在线用户时出错", e);
        }
    }

    public SocketIOClient getUserClient(String userId) {
        return sessions.get(userId);
    }

    private boolean validateSocketToken(String token) {
        try {
            if (!jwtUtil.validateToken(token)) {
                log.warn("JWT验证失败或已过期，exp={}", jwtUtil.getExpirationDateFromToken(token));
                return false;
            }
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

    private String getUserIdFromToken(String token) {
        try {
            String userId = jwtUtil.getUserIdFromToken(token);
            log.info("从token中解析出userId: {}", userId);
            return userId;
        } catch (Exception e) {
            log.error("从token解析用户ID时出错", e);
            return null;
        }
    }

    private void getCurrentOnlineUsers() {
        // TODO: 获取并使用在线用户列表（如需要回传给调用方）
    }

    private void broadcastToCallParticipants(CallSession session, String eventName, Map<String, Object> data, String excludeUserId) {
        for (String participantId : session.getParticipants()) {
            if (!participantId.equals(excludeUserId)) {
                SocketIOClient participantClient = findClientById(participantId);
                if (participantClient != null) {
                    participantClient.sendEvent(eventName, data);
                }
            }
        }
    }
}

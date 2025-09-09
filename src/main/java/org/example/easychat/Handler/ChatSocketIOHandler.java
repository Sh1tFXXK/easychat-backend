package org.example.easychat.Handler;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import lombok.extern.slf4j.Slf4j;
import org.example.easychat.Entity.ChatSession;
import org.example.easychat.Entity.ChatHistory;
import org.example.easychat.Entity.FriendInfo;
import org.example.easychat.Entity.Message;
import org.example.easychat.Entity.User;
import org.example.easychat.Mapper.ChatMapper;
import org.example.easychat.Mapper.UserMapper;
import org.example.easychat.dto.friendVerifyDto;
import org.example.easychat.utils.JwtUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatSocketIOHandler {

    @Autowired
    private SocketIOServer socketIOServer;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ChatMapper chatMapper;

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
     * @param client
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
            log.warn("连接中未提供raw");
            return;
        }

        String token;
        token = URLDecoder.decode(raw, StandardCharsets.UTF_8);
        if(token.startsWith("Bearer ")){
            token = token.substring(7);
        }
        log.info("尝试验证token（已解码/去前缀）：{}",token);

        if (validateSocketToken(token)) {
            String userId = getUserIdFromToken(token);
            if (userId != null && !userId.isEmpty()) {
                client.set("userId", userId);
                log.info("用户认证成功: userId={}", userId);
            } else {
                log.warn("从token中无法解析出有效的用户ID");
            }
        } else {
            log.warn("Token验证失败");
        }
    }

    /**
     * 用户断开连接时处理
     * @param client
     */
    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        log.info("Socket.IO连接已关闭: {}", client.getSessionId());

        // 获取断开连接的用户ID
        String userId = client.get("userId");
        if (userId != null) {
            log.info("用户 {} 断开连接", userId);
            // 从在线用户列表中移除
            onlineUsers.remove(userId);
            sessions.remove(userId);
            // 广播更新后的在线用户列表
            broadcastOnlineUsers();
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
    public void onUserOnline(SocketIOClient client, String userId, Integer status) {
        try {
            // 验证用户ID是否与认证的用户匹配
            String authenticatedUserId = client.get("userId");
            if (authenticatedUserId == null || authenticatedUserId.isEmpty()) {
                log.warn("用户未认证，拒绝上线请求");
                return;
            }

            if (!authenticatedUserId.equals(userId)) {
                log.warn("用户认证失败: authenticatedUserId={}, requestedUserId={}", authenticatedUserId, userId);
                return;
            }

            log.info("用户上线: userId={}, status={}", userId, status);

            // 1. 将用户ID添加到在线用户列表中
            onlineUsers.put(userId, status);
            sessions.put(userId, client);

            // 2. 广播更新后的在线用户列表给所有连接的客户端
            broadcastOnlineUsers();

            // 3. 通知所有用户该用户已上线
            socketIOServer.getBroadcastOperations().sendEvent("userOnline", userId);

            // 4. 向当前用户发送确认消息
            client.sendEvent("onlineConfirmed", "success");

        } catch (Exception e) {
            log.error("处理用户上线时出错", e);
        }
    }

    @OnEvent("offline")
    public void onUserOffline(SocketIOClient client, String userId) {
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
                ackRequest.sendAckData(null, "error");
            }
        }
    }

    @OnEvent("ping")
    public void onPing(SocketIOClient client) {
        client.sendEvent("pong");
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
    /**
     * Listens for the 'changeStatus' event from the client.
     * The client sends the userId and the new status (e.g., 1 for online, 0 for offline).
     */
    @OnEvent("changeStatus")
    public void onChangeStatus(SocketIOClient client, String userId, Integer newStatus) {
       if(newStatus == 1){
           onUserOnline(client, userId, newStatus);
       } else if (newStatus ==0) {
           onUserOffline(client, userId);
       }
    }
    /**
     * 接受好友申请
     */
    @OnEvent("agreeApply")
    public void onAgreeApply( FriendInfo friendInfo, AckRequest ackRequest) {
        String userId = friendInfo.getUserId();
        String friendId = friendInfo.getFriendUserId();
        if(userId == null || friendId == null){
            log.error("请求参数异常");
            return;
        }
        //更新好友申请状态
        userMapper.updateFriendStatus(userId, friendId, 1);

        //创建新的聊天会话ID
        String sessionId = jwtUtil.generateCode();

        //创建双向好友关系
        // 为当前用户添加好友
        friendInfo.setSessionId(sessionId);
        userMapper.insertUserFriend(userId, friendInfo);

        // 为请求方添加好友（需要获取请求方的昵称作为备注）
        User currentUser = userMapper.getUserById(userId);
        FriendInfo friendInfoReverse = new FriendInfo();
        friendInfoReverse.setUserId(friendId);
        friendInfoReverse.setFriendUserId(userId);
        friendInfoReverse.setFriendRemark(currentUser.getNickName());
        friendInfoReverse.setCreateTime(friendInfo.getCreateTime());
        friendInfoReverse.setSessionId(sessionId);
        userMapper.insertUserFriend(friendId, friendInfoReverse);

        //构建返回给客户端的聊天会话对象
        ChatSession chatSession = onCreateChat(userId, friendId);

        //检查请求方是否在线，如果在线则通知
        if (onlineUsers.containsKey(friendId)) {
            SocketIOClient friendClient = sessions.get(friendId);
            if (friendClient != null && friendClient.isChannelOpen()) {
                friendClient.sendEvent("newChat", chatSession);
            }
        }

        //执行回调，将新创建的聊天会话对象返回给客户端
        ackRequest.sendAckData(chatSession);
    }
    /**
     * 拒绝好友申请
     */
    @OnEvent("rejectApply")
    public void onRejectApply(String senderId,String receiverId) {
        if(senderId == null || receiverId == null){
            log.error("请求参数异常");
            return;
        }
        userMapper.updateFriendStatus(senderId, receiverId, 2);
        //检查接收方是否在线，如果在线则通知
        if (onlineUsers.containsKey(receiverId)) {
            SocketIOClient receiverClient = sessions.get(receiverId);
            if (receiverClient != null && receiverClient.isChannelOpen()) {
                receiverClient.sendEvent("rejectApply", senderId);
            }
        }
    }
    /**
     * 创建聊天会话
     */
    @OnEvent("addSession")
    public ChatSession onCreateChat( String userId, String friendId) {
        if(userId == null || friendId == null){
            log.error("请求参数异常");
        }
        User friendUser = userMapper.getUserById(friendId);
        String friendRemark = userMapper.getUserFriendsBaseInfo(userId).get(friendId).getFriendRemark();
        String sessionId = jwtUtil.generateCode();

        ChatSession chatSession = new ChatSession();
        chatSession.setSessionId(sessionId);
        chatSession.setUserId(userId);
        chatSession.setFriendUserId(friendId);
        chatSession.setFriendNickName(friendUser.getNickName());
        chatSession.setFriendAvatar(friendUser.getAvatar());
        chatSession.setFriendRemark(friendRemark);
        chatSession.setCreateTime(String.valueOf(new Date()));
        chatSession.setLatestChatHistory(null);
        return chatSession;
    }
    /**
     * 删除聊天
     */
    @OnEvent("removeSession")
    public void onRemoveChat(String userId, String friendId,AckRequest ackRequest) {
        if(userId == null || friendId == null){
            log.error("请求参数异常");
            return  ;
        }
        chatMapper.removeChat(userId, friendId);
        ackRequest.sendAckData(true);
    }
    /**
     * 發送好友申請
     */
    @OnEvent("sendVerify")
    public void onSendVerify(friendVerifyDto friendVerifyDto, AckRequest ackRequest) {
        userMapper.insertFriendVerify(friendVerifyDto);
        ackRequest.sendAckData(true);
    }
    /**
     * 刪除好友
     */
    @OnEvent("removeFriend")
    public void onRemoveFriend(String userId, String friendId,AckRequest ackRequest) {
        if(userId == null || friendId == null){

            log.error("请求参数异常");
            return;
        }
        userMapper.removeFriend(userId, friendId);
        ackRequest.sendAckData(true);
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
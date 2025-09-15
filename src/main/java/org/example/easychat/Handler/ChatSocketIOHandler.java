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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
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
            log.info("客户端认证状态: authenticatedUserId={}, requestedUserId={}", authenticatedUserId, userId);
            
            if (authenticatedUserId == null || authenticatedUserId.isEmpty()) {
                log.warn("用户未认证，拒绝上线请求: sessionId={}", client.getSessionId());
                return;
            }

            // 使用已认证的用户ID，忽略前端传递的可能为空的userId参数
            String actualUserId = authenticatedUserId;
            log.info("使用已认证的用户ID: {}", actualUserId);

            log.info("用户上线验证通过: userId={}, status={}", actualUserId, status);

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
     * 发送群聊消息处理
     * @param message
     * @param ackRequest
     */
    @OnEvent("sendGroupMsg")
    public void onSendGroupMessage(SocketIOClient client, GroupMessage message, AckRequest ackRequest) {
        try {
            log.info("收到群聊消息: {}", message);
            
            // 从客户端获取已认证的用户ID
            String authenticatedUserId = client.get("userId");
            if (authenticatedUserId == null || authenticatedUserId.isEmpty()) {
                log.warn("用户未认证，拒绝群聊请求");
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData("unauthorized");
                }
                return;
            }
            // 验证发送者ID与认证用户ID是否匹配
            if (!authenticatedUserId.equals(message.getSenderId())) {
                log.warn("发送者ID与认证用户ID不匹配，拒绝群聊请求");
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData(null, "unauthorized");
                }
                return;
            }
            
            //验证用户是否为群成员
            if (!groupChatMapper.isGroupMember(message.getGroupId(), message.getSenderId())) {
                log.warn("用户未加入群组，拒绝群聊请求");
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData("not_member");
                }
                return;
            }
            
            // 获取发送者昵称
            User sender = userMapper.getUserById(message.getSenderId());
            String senderUsername = sender != null ? sender.getNickName() : "未知用户";
            
            // 设置消息发送时间和ID
            message.setSentAt(new java.sql.Timestamp(System.currentTimeMillis()));
            message.setMessageId(jwtUtil.generateCode());
            message.setSenderUsername(senderUsername);
            
            log.info("准备保存群聊消息: {}", message);
            
            //保存消息到数据库
            groupChatMapper.insertGroupMessage(message);
            
            log.info("群聊消息保存成功，开始推送给群成员");
            
            //推送消息给群成员
            List<String> groupMembers = groupChatMapper.getGroupMemberIds(message.getGroupId());
            log.info("群组 {} 的成员列表: {}", message.getGroupId(), groupMembers);
            log.info("当前在线会话数量: {}", sessions.size());
            log.info("当前在线用户: {}", sessions.keySet());
            
            for (String memberId : groupMembers){
                log.info("检查成员 {}, 是否为发送者: {}", memberId, memberId.equals(message.getSenderId()));
                if(!memberId.equals(message.getSenderId())){
                    SocketIOClient memberClient = sessions.get(memberId);
                    if (memberClient != null && memberClient.isChannelOpen()) {
                        memberClient.sendEvent("receiveGroupMsg", message);
                        log.info("✓ 成功推送群聊消息给用户: {}", memberId);
                    } else {
                        log.warn("✗ 用户 {} 不在线或连接已断开", memberId);
                    }
                } else {
                    log.info("跳过发送者自己: {}", memberId);
                }
            }
            // 发送成功响应
            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData("success");
            }
            log.info("群聊消息处理完成");

        } catch (Exception e) {
            log.error("处理发送群消息时出错", e);
            // 发送错误响应
            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData(null, "error");
            }
        }
    }

    /**
     * 发送语音消息
     */
    @OnEvent("sendVoice")
    public void onSendVoice(SocketIOClient client, VoiceMessage message, AckRequest ackRequest) {
        try {
            log.info("收到语音消息: {}", message);
            String authenticatedUserId = client.get("userId");
            if (authenticatedUserId == null || authenticatedUserId.isEmpty()) {
                log.warn("用户未认证，拒绝语音请求");
                if(ackRequest.isAckRequested()){
                    ackRequest.sendAckData(null, "unauthorized");
                }
                return;
            }

            // 验证发送者ID与认证用户ID是否匹配
            if(!Objects.equals(authenticatedUserId, message.getSenderId())){
                log.warn("发送者ID与认证用户ID不匹配，拒绝语音请求");
                if(ackRequest.isAckRequested()){
                    ackRequest.sendAckData(null, "unauthorized");
                }
                return;
            }
            //文件格式和大小
            if(!ValidationUtils.isValidAudioFile(message.getFileName())){
                log.warn("文件格式错误");
                if(ackRequest.isAckRequested()){
                    ackRequest.sendAckData(null, "error");
                }
            }
            if(message.getFileSize()>10*1024*1024){
                log.warn("文件过大");
                if(ackRequest.isAckRequested()){
                    ackRequest.sendAckData(null, "error");
                }
            }
            //时长 语音时长验证（单位：秒）
            if (message.getDuration() != null) {
                if (message.getDuration() > 60){
                    log.warn("语音时长超过60秒");
                    if(ackRequest.isAckRequested()){
                        ackRequest.sendAckData(null,"duration  too long ");
                    }
                    return;
                }
                if (message.getDuration() < 1){
                    log.warn("语音时长小于1秒");
                    if (ackRequest.isAckRequested()){
                        ackRequest.sendAckData(null, "duration too short");
                    }
                    return;
                }
            }

            // 设置消息时间戳
            message.setCreateTime(java.sql.Date.valueOf(LocalDate.now()));
            message.setId(Long.valueOf(jwtUtil.generateCode()));

            // 保存语音消息到数据库
            VoiceMessage savedMessage = chatMapper.insertVoiceMessage(message);

            // 获取发送者信息
            User sender = userMapper.getUserById(message.getSenderId());
            String senderUsername = sender != null ? sender.getNickName() : "未知用户";

            // 构建要广播的消息
            Map<String, Object> broadcastData = new HashMap<>();
            broadcastData.put("messageId", savedMessage.getId());
            broadcastData.put("senderId", message.getSenderId());
            broadcastData.put("senderName", senderUsername);
            broadcastData.put("receiverId", message.getReceiverId());
            broadcastData.put("voiceUrl", message.getFileUrl());
            broadcastData.put("duration", message.getDuration());
            broadcastData.put("createTime", message.getCreateTime());
            broadcastData.put("type", "voice");
            broadcastData.put("fileName", message.getFileName());
            broadcastData.put("fileSize", message.getFileSize());

            // 判断是群聊还是私聊（假设群ID以"group_"开头）

            if(message.getChatType()==1){
                // 群聊消息处理
                String groupId = message.getReceiverId();

                // 获取群成员列表
                List<String> groupMembers = chatMapper.getGroupMemberIds(groupId);
                if (groupMembers == null || groupMembers.isEmpty()) {
                    log.warn("群组 {} 没有成员", groupId);
                    if(ackRequest.isAckRequested()){
                        ackRequest.sendAckData(null, "group_not_found");
                    }
                    return;
                }

                // 记录成功发送数量
                int successCount = 0;

                // 向每个群成员发送消息（除了发送者自己）
                for (String memberId : groupMembers) {
                    if (!memberId.equals(message.getSenderId())) {
                        SocketIOClient memberClient = findClientById(memberId);
                        if (memberClient != null && memberClient.isChannelOpen()) {
                            memberClient.sendEvent("receiveGroupVoice", broadcastData);
                            successCount++;
                            log.debug("语音消息已发送给群成员: {}", memberId);
                        } else {
                            log.debug("群成员 {} 离线", memberId);
                        }
                    }
                }

                log.info("群语音消息发送完成，成功发送给 {} 个在线成员", successCount);
            }else {
                // 私聊消息处理
                SocketIOClient receiverClient = findClientById(message.getReceiverId());

                if (receiverClient != null && receiverClient.isChannelOpen()){
                    receiverClient.sendEvent("receiveVoice", broadcastData);
                    log.info("发送语音消息给接收者成功");
                }else {
                    // 接收者离线
                    log.info("接收者 {} 离线，保存为离线消息", message.getReceiverId());
                    // 可以在这里添加推送通知逻辑
                    // pushNotificationService.sendOfflineVoiceNotification(receiverId, senderUsername);
                }
            }

            // 发送确认给发送者
            if(ackRequest.isAckRequested()){
                Map<String, Object> ackData = new HashMap<>();
                ackData.put("success", true);
                ackData.put("messageId", savedMessage.getId());
                ackData.put("timestamp", message.getCreateTime());
                ackData.put("isGroup", message.getChatType());
                ackRequest.sendAckData(ackData);
            }

        } catch (Exception e) {
            log.error("处理语音消息时发生错误", e);

            // 发送错误响应
            if(ackRequest.isAckRequested()){
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("success", false);
                errorData.put("error", "发送语音消息失败: " + e.getMessage());
                ackRequest.sendAckData(errorData);
            }
        }
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

    //存储当前通话状态
    private final  Map<String, CallSession> activeCalls = new ConcurrentHashMap<>();
    /**
     * 发起语音通话
     * @param client
     */
    @OnEvent("startVoiceCall")
    public void onStartVoiceCall(SocketIOClient client, VoiceCallRequest request, AckRequest ackRequest) {
        try {
            String callerId = client.get("userId");
            if(callerId == null || !callerId.equals(request.getCallerId())){
                ackRequest.sendAckData(null, "invalid_caller_id");
            }
            //检查是否在已在通话中
            if(isUserInCall(callerId)){
                ackRequest.sendAckData(null, "already_in_call");
            }
            //生成通话ID
            String callId = UUID.randomUUID().toString();

            //创建通话会话
            CallSession callSession = new CallSession();
            callSession.setCallId(callId);
            callSession.setCallerId(callerId);
            callSession.setReceiverId(request.getReceiverId());
            callSession.setCallType(request.getCallType());
            callSession.setStatus("calling");
            callSession.setStartTime(System.currentTimeMillis());

            activeCalls.put(callId, callSession);

            //获取主叫方消息
            User caller = userMapper.getUserById(callerId);
            //构建通话请求数据
            Map<String, Object> callerData = new HashMap<>();
            callerData.put("callId", callId);
            callerData.put("caller", caller);
            callerData.put("sdpOffer", request.getSdpOffer());
            callerData.put("callType", request.getCallType());
            callerData.put("callerName", caller.getNickName());
            callerData.put("callerAvatar", caller.getAvatar());

            // 判断是否为群组通话
            boolean isGroupCall = request.getReceiverId().startsWith("group_");
            if (isGroupCall){
                //群组通话
                callSession.setGroupCall(true);
                callSession.setGroupId(request.getReceiverId());

                //获取群成员
                List<String> groupMembers = chatMapper.getGroupMemberIds(request.getReceiverId());
                callSession.setParticipants(new HashSet<>(groupMembers));

                //通知所有在线成员
                for (String membersId : groupMembers){
                    if (!membersId.equals(callerId)){
                        SocketIOClient memberClient = findClientById(membersId);
                        if (memberClient !=null){
                            memberClient.sendEvent("incomingFroupCall",callerData);
                        }
                    }
                }
            }else {
                //一对一通话
                log.info("开始查找接收方 {} 的客户端连接", request.getReceiverId());
                SocketIOClient receiverClient = findClientById(request.getReceiverId());

                if(receiverClient == null){
                    log.warn("接收方 {} 不在线，无法发起通话", request.getReceiverId());
                    activeCalls.remove(callId);
                    ackRequest.sendAckData(null,"receiver_offline");
                    return;
                }
                
                log.info("找到接收方 {} 的客户端连接: {}", request.getReceiverId(), receiverClient.getSessionId());
                
                // 检查接收方是否在通话中
                log.info("检查接收方 {} 是否在通话中", request.getReceiverId());
                if(isUserInCall(request.getReceiverId())){
                    log.warn("接收方 {} 正在通话中，拒绝新的通话请求", request.getReceiverId());
                    activeCalls.remove(callId);
                    ackRequest.sendAckData(null,"receiver_busy");
                    return;
                }
                
                log.info("接收方 {} 不在通话中，准备发送来电通知", request.getReceiverId());
                log.info("发送 incomingCall 事件到客户端 {}, 数据: {}", receiverClient.getSessionId(), callerData);
                
                try {
                    receiverClient.sendEvent("incomingCall", callerData);
                    log.info("成功发送 incomingCall 事件到接收方 {}", request.getReceiverId());
                } catch (Exception e) {
                    log.error("发送 incomingCall 事件失败: ", e);
                    activeCalls.remove(callId);
                    ackRequest.sendAckData(null, "send_event_failed");
                    return;
                }
            }

            // 返回成功响应
            Map<String,Object> response = new HashMap<>();
            response.put("success",true);
            response.put("callId",callId);
            ackRequest.sendAckData(response);

        }catch (Exception e){
            log.warn("发起通话失败",e);
            ackRequest.sendAckData(null,"error");
        }

    }
    /**
     * 接听通话
     */
    @OnEvent("answerCall")
    public void onAnswerCall(SocketIOClient client, AnswerCallRequest request, AckRequest ackRequest){
        try{
            String userId = client.get("userId");
            CallSession session = activeCalls.get(request.getCallId());

            if (session == null) {
                ackRequest.sendAckData(null, "call_not_found");
            }

            // 更新通话状态
            if (session != null) {
                session.setStatus("connected");
            }
            if (session != null) {
                session.setConnectedTime(System.currentTimeMillis());
            }
            if (session.isGroupCall()) {
                // 群组通话：添加参与者
                session.getActiveParticipants().add(userId);

                // 通知所有参与者有新成员加入
                broadcastToCallParticipants(session, "participantJoined", Map.of(
                        "userId", userId,
                        "sdpAnswer", request.getSdpAnswer()
                ), userId);
            }else {
                // 一对一通话：更新通话信息
                SocketIOClient callerClient = findClientById(session.getCallerId());
                if (callerClient != null){
                    Map<String, Object> response = new HashMap<>();
                    response.put("callId",request.getCallId());
                    response.put("sdpAnswer",request.getSdpAnswer());
                    callerClient.sendEvent("callAnswered",response);
                }
            }

            // 返回成功响应
            ackRequest.sendAckData(Map.of("success", true));
        }catch (Exception e){
            log.warn("接听通话失败",e);
            ackRequest.sendAckData(null,"error");
        }
    }
    /**
     * 拒绝通话
     */
    @OnEvent("rejectCall")
    public void onRejectCall(SocketIOClient client, RejectCallRequest request, AckRequest ackRequest){
        try{
            String userId = client.get("userId");
            CallSession session = activeCalls.get(request.getCallId());

            if (session == null) {
                ackRequest.sendAckData(null, "call_not_found");
            }
            // 拒绝通话
            SocketIOClient callerClient = findClientById(session.getCallerId());
            if (callerClient != null){
                Map<String, Object> response = new HashMap<>();
                response.put("callId",request.getCallId());
                response.put("reason",request.getReason());
                callerClient.sendEvent("callRejected",response);
            }
            // 移除通话
            activeCalls.remove(request.getCallId());
            ackRequest.sendAckData(Map.of("success", true));
        }catch (Exception e){
            log.warn("拒绝通话失败",e);
            ackRequest.sendAckData(null,"error");
        }
    }
    /**
     * 挂断通话
     */
    @OnEvent("endCall")
    public void onEndCall(SocketIOClient client, String callId, AckRequest ackRequest){
        try{
            String userId = client.get("userId");
            log.info("用户 {} 请求结束通话: {}", userId, callId);
            
            CallSession session = activeCalls.get(callId);
            if (session == null) {
                log.warn("未找到通话会话，callId: {}", callId);
                ackRequest.sendAckData(null, "call_not_found");
                return;
            }

            if (session.isGroupCall()) {
                // 群组通话：移除参与者
                session.getActiveParticipants().remove(userId);
                // 通知所有参与者有成员退出
                if (!session.getActiveParticipants().isEmpty()){
                    broadcastToCallParticipants(session, "participantLeft", Map.of(
                            "userId", userId
                    ), userId);
                }else {
                    // 通话结束
                    activeCalls.remove(callId);
                }
            }else {
                // 一对一通话：结束通话
                String otherUserId = session.getCallerId().equals(userId) ? session.getReceiverId() : session.getCallerId();
                SocketIOClient otherUserClient = findClientById(otherUserId);
                if (otherUserClient != null){
                    otherUserClient.sendEvent("callEnded", Map.of("callId", callId));
                    endCallSession(callId);
                }
                ackRequest.sendAckData(Map.of("success", true));
            }
        }catch (Exception e){
            log.warn("挂断通话失败",e);
            ackRequest.sendAckData(null,"error");
        }
    }

    /**
     * 清理过期的通话会话
     */
    private void cleanupExpiredCalls() {
        long currentTime = System.currentTimeMillis();
        long expireTime = 2 * 60 * 1000; // 2分钟超时（缩短超时时间）
        
        int beforeSize = activeCalls.size();
        activeCalls.entrySet().removeIf(entry -> {
            CallSession session = entry.getValue();
            long duration = currentTime - session.getStartTime();
            
            // 清理条件：
            // 1. 超过2分钟的会话
            // 2. 状态为 "calling" 且超过30秒的会话（可能是未接听的通话）
            boolean isExpired = duration > expireTime || 
                              ("calling".equals(session.getStatus()) && duration > 30 * 1000);
            
            if (isExpired) {
                log.info("清理过期通话会话: {}, 状态: {}, 持续时间: {}秒", 
                    entry.getKey(), session.getStatus(), duration / 1000);
            }
            return isExpired;
        });
        
        int afterSize = activeCalls.size();
        if (beforeSize != afterSize) {
            log.info("清理过期通话会话完成，清理前: {}, 清理后: {}", beforeSize, afterSize);
        }
    }

    /**
     * 强制清理所有通话会话（调试用）
     */
    public void forceCleanupAllCalls() {
        int size = activeCalls.size();
        activeCalls.clear();
        log.warn("强制清理所有通话会话，共清理 {} 个会话", size);
    }

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
     * 检查用户是否在通话中（排除当前客户端）
     */
    private boolean isUserInCall(String userId) {
        return isUserInCall(userId, null);
    }
    
    /**
     * 检查用户是否在通话中（可排除指定客户端）
     */
    private boolean isUserInCall(String userId, SocketIOClient excludeClient) {
        // 先清理过期的通话会话
        cleanupExpiredCalls();
        
        log.info("检查用户 {} 是否在通话中，当前活跃通话数: {}", userId, activeCalls.size());
        
        // 打印所有活跃通话会话的详细信息
        for (Map.Entry<String, CallSession> entry : activeCalls.entrySet()) {
            CallSession session = entry.getValue();
            log.info("活跃通话会话 - ID: {}, 发起者: {}, 接收者: {}, 状态: {}, 开始时间: {}", 
                entry.getKey(), session.getCallerId(), session.getReceiverId(), 
                session.getStatus(), new java.util.Date(session.getStartTime()));
        }
        
        boolean inCall = activeCalls.values().stream()
                .anyMatch(session -> {
                    // 检查会话状态是否为已连接
                    if (!"connected".equals(session.getStatus())) {
                        log.info("跳过非连接状态的会话: {}, 状态: {}", session.getCallId(), session.getStatus());
                        return false;
                    }
                    
                    boolean matches = session.getCallerId().equals(userId) ||
                                    session.getReceiverId().equals(userId) ||
                                    (session.isGroupCall() && session.getActiveParticipants().contains(userId));
                    
                    if (matches) {
                        log.info("用户 {} 匹配到活跃通话会话 - 发起者: {}, 接收者: {}, 状态: {}", 
                            userId, session.getCallerId(), session.getReceiverId(), session.getStatus());
                    }
                    
                    return matches;
                });
        
        log.info("用户 {} 通话状态检查结果: {}", userId, inCall);
        
        return inCall;
    }
    /**
     * 交换ICE候选
     */
    @OnEvent("iceCandidate")
    public void onIceCandidate(SocketIOClient client, IceCandidateRequest request, AckRequest ackRequest) {
        try {
            String userId = client.get("userId");
            CallSession session = activeCalls.get(request.getCallId());
            // 添加对 session 为 null 的检查
            if (session == null) {
                log.warn("未找到通话会话，callId: {}", request.getCallId());
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData(null, "call_not_found");
                }
                return;
            }
            Map<String, Object> response = new HashMap<>();
            response.put("callId", request.getCallId());
            response.put("candidate", request.getCandidate());
            response.put("fromUserId",userId);

            if (session.isGroupCall()){
                // 群组通话：广播ICE候选
                if(request.getTargetUserId()!= null){
                    SocketIOClient targetUserClient = findClientById(request.getTargetUserId());
                    if (targetUserClient != null){
                        targetUserClient.sendEvent("iceCandidate", response);
                    }
                }
            }else {
                // 一对一通话：发送ICE候选
                String targetUserId = session.getCallerId().equals(userId) ? session.getReceiverId() : session.getCallerId();
                SocketIOClient targetUserClient = findClientById(targetUserId);
                if (targetUserClient != null){
                    targetUserClient.sendEvent("iceCandidate", response);
                }
            }
        }catch (Exception e){
            log.warn("交换ICE候选失败",e);
        }
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
     * 结束通话会话
     */
    private void endCallSession(String callId) {
        CallSession session = activeCalls.remove(callId);
        if (session != null) {
            // 记录通话日志
            long duration = System.currentTimeMillis() - session.getStartTime();
            log.info("通话结束 - ID: {}, 时长: {}秒", callId, duration / 1000);

            // 保存通话记录到数据库
            saveCallRecord(session, duration);
        }
    }
    /**
     * 保存通话记录到数据库
     */
    private void saveCallRecord(CallSession session, long duration) {
        try {
            CallRecord callRecord = new CallRecord();
            callRecord.setCallId(session.getCallId());
            callRecord.setCallerId(session.getCallerId());
            callRecord.setReceiverId(session.getReceiverId());
            callRecord.setGroupId(session.isGroupCall() ? session.getGroupId() : null);
            callRecord.setCallType(session.getCallType());
            callRecord.setDuration(duration);
            callRecord.setStartTime(new Date(session.getStartTime()));
            callRecord.setEndTime(new Date(session.getStartTime() + duration));

            chatMapper.insertCallRecord(callRecord);
            log.info("通话记录已保存: {}", callRecord);
        } catch (Exception e) {
            log.error("保存通话记录失败", e);
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
           onUserOnline(userId, newStatus, client);
       } else if (newStatus ==0) {
           onUserOffline(userId);
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
package org.example.easychat.service;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.BO.ApiResponseBO;
import org.example.easychat.Entity.ChatSession;
import org.example.easychat.Entity.ChatHistory;
import org.example.easychat.Entity.PageResult;
import org.example.easychat.Mapper.ChatMapper;
import org.example.easychat.utils.AliOSSUtil;
import org.example.easychat.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class ChatService implements ChatInterface{

    @Autowired
    private ChatMapper chatMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private AliOSSUtil aliOSSUtil;

    @Autowired
    private JwtUtil jwtUtil;

    public List<ChatSession> getChats(String userId) {
        List<ChatSession> chatBOList = chatMapper.getChats(userId);
        for(ChatSession chatBO:chatBOList){
            chatBO.setLatestChatHistory(chatMapper.getLatestChatHistoryBySessionId(chatBO.getSessionId()));
        }
        return chatBOList;
    }
    
    @Override
    public List<ChatSession> getChatList(String userId) {
        return chatMapper.getChats(userId);
    }

    @Override
    public List<ChatHistory> getChatHistoryList(String sessionId) {
        return chatMapper.getChatHistory(sessionId);
    }

    @Override
    public PageResult<ChatHistory> getChatPage(String userId, String sessionId, int current, Integer pageSize) {
        List<ChatHistory> chatHistory = getChatHistoryList(sessionId);
        long total = chatMapper.getTotal(sessionId);
        return new PageResult<>(chatHistory, total, current, pageSize);
    }
    @Override
    public ApiResponseBO savePictureMsg(MultipartFile file, String senderId,
                                        String receiverId, String sessionId) {
        //2.验证图片格式和大小
        if (file.isEmpty()) {
            throw new IllegalArgumentException("图片不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("文件格式不正确，仅支持图片格式");
        }
        long size = file.getSize();
        if (size > 2 * 1024 * 1024) { // 5MB
            throw new IllegalArgumentException("图片大小不能超过2MB");
        }
        // 保存图片
        try {
            String url = aliOSSUtil.uploadImage(file);
            return ApiResponseBO.success(url);
        } catch (IOException e) {
            throw new RuntimeException("图片上传失败");
        }

    }
    @Override
    public ApiResponseBO saveFileMsg(MultipartFile file, String senderId,
                                     String receiverId, String sessionId) {
        //2.验证
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        long size = file.getSize();
        if (size > 30 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过30MB");
        }
        // 保存图片
        try {
            String url = aliOSSUtil.uploadFile(file);
            return ApiResponseBO.success(url);

        } catch (IOException e) {
            throw new RuntimeException("文件上传失败");
        }
    }

    @Override
    public ApiResponseBO uploadVoice(MultipartFile file, String senderId,
                                     String receiverId, String chatType) {
        // 验证语音文件
        if (file.isEmpty()) {
            throw new IllegalArgumentException("语音文件不能为空");
        }
        
        // 验证文件格式
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("audio/")) {
            throw new IllegalArgumentException("文件格式不正确，仅支持音频格式");
        }
        
        // 验证文件大小 (最大10MB)
        long size = file.getSize();
        if (size > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("语音文件大小不能超过10MB");
        }
        
        try {
            // 上传语音文件到OSS
            String url = aliOSSUtil.uploadFile(file);
            log.info("语音文件上传成功: {}", url);
            return ApiResponseBO.success(url);
            
        } catch (IOException e) {
            log.error("语音文件上传失败", e);
            throw new RuntimeException("语音文件上传失败");
        }
    }

}
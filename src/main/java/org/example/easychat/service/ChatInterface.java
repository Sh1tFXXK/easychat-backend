package org.example.easychat.service;

import org.example.easychat.BO.ApiResponseBO;
import org.example.easychat.Entity.ChatSession;
import org.example.easychat.Entity.ChatHistory;
import org.example.easychat.Entity.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatInterface {
    /**
     * 获取用户聊天列表
     * @param userId 用户ID
     * @return 聊天列表
     */
    List<ChatSession> getChats(String userId);
    
    List<ChatSession> getChatList(String userId);
    
    List<ChatHistory> getChatHistoryList(String sessionId);

    PageResult<ChatHistory> getChatPage(String userId, String sessionId, int current, Integer pageSize);


    ApiResponseBO savePictureMsg(MultipartFile file, String senderId,
                                 String receiverId, String sessionId);

    ApiResponseBO saveFileMsg(MultipartFile file, String senderId,
                              String receiverId, String sessionId);

    ApiResponseBO uploadVoice(MultipartFile file, String senderId,
                              String receiverId, String chatType);
}
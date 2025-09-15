package org.example.easychat.controller;

import org.example.easychat.BO.ApiResponseBO;
import org.example.easychat.Entity.ChatHistory;
import org.example.easychat.Entity.ChatSession;
import org.example.easychat.BO.ResponseBO;
import org.example.easychat.Entity.PageResult;
import org.example.easychat.service.ChatService;
import org.example.easychat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserService userService;

    /**
     * 获取聊天列表
     */
    @GetMapping("chats")
    public ResponseBO<List<ChatSession>> getChats(@RequestParam("id") String userId){
        if(userId == null){
            return ResponseBO.error("用户ID不能为空");
        }
        List<ChatSession> chatBOList =chatService.getChats(userId);
        return ResponseBO.success(chatBOList);
    }

    /**
     * 获取聊天历史
     */
    @GetMapping("chats/chatHistory")
    public ResponseBO<PageResult<ChatHistory>> getChatHistory(@RequestParam("id")    String userId,
                                                              @RequestParam("session") String sessionId,
                                                              @RequestParam("page") int current,
                                                              @RequestParam("size") int pageSize){
        PageResult<ChatHistory> pageResult = chatService.getChatPage(userId,sessionId,current, pageSize);
        return ResponseBO.success(pageResult);
    }

    /**
     * 发送图片消息
     */

    @PostMapping("chats/savePictureMsg")
    public ApiResponseBO savePictureMsg(@RequestParam("file") MultipartFile file,
                                        @RequestParam("senderId") String senderId,
                                        @RequestParam("receiverId") String receiverId,
                                        @RequestParam("sessionId") String sessionId) {
        return chatService.savePictureMsg(file, senderId, receiverId, sessionId);
    }
    /**
     *  发送文件消息
     */
    @PostMapping("chats/saveFileMsg")
    public ApiResponseBO saveFileMsg(@RequestParam("file") MultipartFile file,
                                   @RequestParam("senderId") String senderId,
                                   @RequestParam("receiverId") String receiverId,
                                   @RequestParam("sessionId") String sessionId) {
        return chatService.saveFileMsg(file, senderId, receiverId, sessionId);
    }

    /**
     * 上传语音文件
     */
    @PostMapping("chats/uploadVoice")
    public ApiResponseBO uploadVoice(@RequestParam("file") MultipartFile file,
                                   @RequestParam("senderId") String senderId,
                                   @RequestParam("receiverId") String receiverId,
                                   @RequestParam("chatType") String chatType) {
        return chatService.uploadVoice(file, senderId, receiverId, chatType);
    }

}

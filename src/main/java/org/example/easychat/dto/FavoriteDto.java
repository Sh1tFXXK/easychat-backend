package org.example.easychat.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 收藏DTO
 */
@Data
public class FavoriteDto {
    
    /**
     * 收藏类型: message, image, file, voice, video, contact
     */
    @NotBlank(message = "收藏类型不能为空")
    private String itemType;
    
    /**
     * 收藏标题
     */
    @Size(max = 200, message = "标题长度不能超过200字符")
    private String title;
    
    /**
     * 收藏内容
     */
    private String content;
    
    /**
     * 分类ID
     */
    private Long categoryId;
    
    /**
     * 标签列表
     */
    private List<String> tags;
    
    /**
     * 用户备注
     */
    @Size(max = 500, message = "备注长度不能超过500字符")
    private String note;
    
    /**
     * 原始数据
     */
    private OriginalData originalData;
    
    /**
     * 来源聊天信息
     */
    private SourceChat sourceChat;
    
    /**
     * 文件大小
     */
    private Long fileSize;
    
    /**
     * 文件类型
     */
    private String fileType;

    @Data
    public static class OriginalData {
        private String messageId;
        private Long senderId;
        private String senderName;
        private String createTime;
        private String messageType;
        private String duration; // 语音时长
        private String fileName; // 文件名
        
        public Long getSenderId() {
            return senderId;
        }
        
        public String getSenderName() {
            return senderName;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        public String getDuration() {
            return duration;
        }
    }
    
    @Data
    public static class SourceChat {
        private String chatId;
        private String chatName;
        private String chatType; // private, group
        
        public String getChatId() {
            return chatId;
        }
        
        public String getChatName() {
            return chatName;
        }
        
        public String getChatType() {
            return chatType;
        }
    }
    
    // 显式添加getter方法以确保编译通过
    public String getItemType() {
        return itemType;
    }
    
    public String getContent() {
        return content;
    }
    
    public Long getCategoryId() {
        return categoryId;
    }
    
    public String getNote() {
        return note;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public String getFileType() {
        return fileType;
    }
    
    public OriginalData getOriginalData() {
        return originalData;
    }
    
    public SourceChat getSourceChat() {
        return sourceChat;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public String getTitle() {
        return title;
    }
}

package org.example.easychat.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收藏实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("favorites")
public class Favorite {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 收藏类型: message, image, file, voice, video, contact
     */
    @TableField("item_type")
    private String itemType;
    
    /**
     * 收藏标题
     */
    @TableField("title")
    private String title;
    
    /**
     * 收藏内容
     */
    @TableField("content")
    private String content;
    
    /**
     * 原始数据(JSON格式)
     */
    @TableField("original_data")
    private String originalData;
    
    /**
     * 分类ID
     */
    @TableField("category_id")
    private Long categoryId;
    
    /**
     * 标签(JSON数组格式)
     */
    @TableField("tags")
    private String tags;
    
    /**
     * 用户备注
     */
    @TableField("note")
    private String note;
    
    /**
     * 来源聊天ID
     */
    @TableField("source_chat_id")
    private String sourceChatId;
    
    /**
     * 来源聊天名称
     */
    @TableField("source_chat_name")
    private String sourceChatName;
    
    /**
     * 来源聊天类型: private, group
     */
    @TableField("source_chat_type")
    private String sourceChatType;
    
    /**
     * 发送者ID
     */
    @TableField("sender_id")
    private Long senderId;
    
    /**
     * 发送者名称
     */
    @TableField("sender_name")
    private String senderName;
    
    /**
     * 文件大小(字节)
     */
    @TableField("file_size")
    private Long fileSize;
    
    /**
     * 文件类型
     */
    @TableField("file_type")
    private String fileType;
    
    /**
     * 是否可用(文件是否存在)
     */
    @TableField("is_available")
    private Boolean isAvailable;
    
    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    /**
     * 逻辑删除标志
     */
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;
    
    // 显式添加setter方法以确保编译通过
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public void setIsAvailable(Boolean isAvailable) {
        this.isAvailable = isAvailable;
    }
}
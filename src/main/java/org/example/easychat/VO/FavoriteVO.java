package org.example.easychat.VO;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收藏VO
 */
@Data
public class FavoriteVO {
    
    /**
     * 收藏ID
     */
    private Long id;
    
    /**
     * 收藏类型
     */
    private String itemType;
    
    /**
     * 收藏标题
     */
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
     * 分类名称
     */
    private String categoryName;
    
    /**
     * 分类颜色
     */
    private String categoryColor;
    
    /**
     * 分类图标
     */
    private String categoryIcon;
    
    /**
     * 标签列表
     */
    private List<String> tags;
    
    /**
     * 用户备注
     */
    private String note;
    
    /**
     * 来源聊天名称
     */
    private String sourceChatName;
    
    /**
     * 来源聊天类型
     */
    private String sourceChatType;
    
    /**
     * 发送者名称
     */
    private String senderName;
    
    /**
     * 文件大小
     */
    private Long fileSize;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * 是否可用
     */
    private Boolean isAvailable;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 原始数据（JSON字符串）
     */
    private String originalData;
}

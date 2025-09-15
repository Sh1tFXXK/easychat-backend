package org.example.easychat.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.sql.Date;

@Data
@TableName("voice_messages")
public class VoiceMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String senderId;
    private String receiverId;
    private String fileName;
    private String fileUrl;
    private Long duration;
    private Date createTime;
    private Long fileSize;
    private Integer chatType;
}

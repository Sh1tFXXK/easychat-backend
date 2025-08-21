package org.example.easychat.dto;


import lombok.Data;

@Data
public class friendVerifyDto {
     private String senderId ;
     private String senderNickName ;
     private String senderAvatar ;
     private String receiverId ;
     private String receiverNickName;
     private String receiverAvatar;
     private String applyReason;
     private String remark;
     private Integer status;
     private Integer hasRead;
     private String createTime;
}

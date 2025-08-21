package org.example.easychat.dto;

import lombok.Data;

@Data
public class SendVerifyCodeDto {
    private String email;
    private String type;
    private String VerifyCode;

}
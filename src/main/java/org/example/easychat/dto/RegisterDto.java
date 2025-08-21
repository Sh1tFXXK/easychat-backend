package org.example.easychat.dto;

import lombok.Data;

@Data
public class RegisterDto {
    private String username;
    private String nickName;
    private String password;
    private String email;
    private String verifyCode;
}

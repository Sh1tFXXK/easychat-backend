package org.example.easychat.dto;

import lombok.Data;

@Data
public class ChangePasswordDto {
    private String userId;
    private String checkPassword;
    private String newPassword;
}

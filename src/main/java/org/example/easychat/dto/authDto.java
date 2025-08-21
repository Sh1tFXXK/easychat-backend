package org.example.easychat.dto;

import lombok.Data;

@Data
public class authDto {
    private String username;
    private String password;
    private boolean loginFree;
}

package org.example.easychat.BO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.easychat.Entity.UserInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginBO {
    private String userId;
    private String token;
    private UserInfo userInfo;

    public LoginBO(String userId, String token) {
        this.userId = userId;
        this.token = token;
    }
}


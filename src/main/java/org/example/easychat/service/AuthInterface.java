package org.example.easychat.service;

import org.example.easychat.BO.LoginBO;
import org.example.easychat.dto.authDto;

public interface AuthInterface {
    /**
     * 登录
     * @param authDto
     */
     LoginBO login(authDto authDto);
    /**
     * 登出
     */
    void logout(String token);
}

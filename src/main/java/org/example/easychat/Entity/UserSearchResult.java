package org.example.easychat.Entity;

import lombok.Data;

import java.util.List;

@Data
public class UserSearchResult {
    private String id;           // 用户ID
    private String username;     // 用户名
    private String nickName;     // 昵称
    private String avatar;       // 头像路径
    private Integer gender;      // 性别
    private String introduction; // 个人介绍
    private List<String> tags;   // 标签列表
}
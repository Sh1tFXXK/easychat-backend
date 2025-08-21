package org.example.easychat.Entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfo {
    private String id;           // 用户ID
    private String username;     // 用户名
    private String nickName;     // 昵称
    private String avatar;       // 头像URL
    private Integer gender;      // 性别: 0-女, 1-男, 2-未知
    private String email;        // 邮箱
    private Integer status;      // 在线状态: 0-隐身, 1-在线
    private List<String> tags;   // 标签列表
    private String createTime;   // 创建时间
}
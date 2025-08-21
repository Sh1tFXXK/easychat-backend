package org.example.easychat.Entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
@TableName("users")
public class User {

    @JsonSerialize(using = ToStringSerializer.class)
    private String id;
    private String username;
    @TableField("nickname")
    private String nickName;
    private String password;
    private String email;
    private String phone;
    private String avatar;
    private String gender;
    private String birthday;
    private String region;
    private String introduction;
    private Integer status;
    private String createTime;
    private String updateTime;
    private String tags;
}

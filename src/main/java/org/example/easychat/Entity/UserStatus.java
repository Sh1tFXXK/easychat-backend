package org.example.easychat.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.time.LocalDateTime;

@Data
@TableName("user_status")
public class UserStatus {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @TableField("status")
    @Pattern(regexp = "^(online|offline|away|busy)$", message = "状态必须是online、offline、away或busy")
    private String status;
    
    @TableField("last_active_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastActiveTime;
    
    @TableField("socket_id")
    private String socketId;
    
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
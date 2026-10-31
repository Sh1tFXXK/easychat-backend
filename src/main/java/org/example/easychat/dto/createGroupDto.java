package org.example.easychat.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class createGroupDto {
    private String groupName;
    @TableId(type = IdType.ASSIGN_ID)
    private String groupId ;
    private String ownerId;
    private String createdAt;
}

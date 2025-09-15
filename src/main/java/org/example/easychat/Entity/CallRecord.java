package org.example.easychat.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("call_record")
public class CallRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String callId;
    private String callerId;
    private String receiverId;
    private String groupId;
    private String callType;
    private Long duration;
    private Date startTime;
    private Date endTime;
}
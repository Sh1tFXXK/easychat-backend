package org.example.easychat.dto;

import lombok.Data;

/**
 * 接听通话请求
 */
@Data
public class AnswerCallRequest {
    private String callId;// 通话ID
    private String sdpAnswer; // SDP应答
}
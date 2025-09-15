package org.example.easychat.dto;

import lombok.Data;

@Data
public class RejectCallRequest {
    private String callId;
    private String reason;
}

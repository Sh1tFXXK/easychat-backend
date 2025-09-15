package org.example.easychat.Entity;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class CallSession {
    private String callId;
    private String callerId;
    private String receiverId;
    private String callType; // audio/video
    private String status; // calling/connected/ended
    private Long startTime;
    private Long connectedTime;
    private boolean groupCall;
    private String groupId;
    private Set<String> participants = new HashSet<>(); // 所有参与者
    private Set<String> activeParticipants = new HashSet<>(); // 当前在线参与者
}
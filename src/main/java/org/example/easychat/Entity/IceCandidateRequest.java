package org.example.easychat.Entity;

import lombok.Data;

@Data
public class IceCandidateRequest {
    static class IceCandidate {
        private String sdpMid;
        private int sdpMLineIndex;
        private String candidate;
    }
    private String callId;
    private IceCandidate candidate;
    private String targetUserId; // 群组通话时指定目标
}
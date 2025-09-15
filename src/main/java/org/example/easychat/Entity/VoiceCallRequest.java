package org.example.easychat.Entity;

import lombok.Data;

@Data
public class VoiceCallRequest {
    static class SdpOffer {
        private String sdp;
        private String type;
    }
    private String callerId;
    private String receiverId;
    private String callType;
    private SdpOffer sdpOffer;
}
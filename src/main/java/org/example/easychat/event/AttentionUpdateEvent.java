package org.example.easychat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 特别关心更新事件
 */
@Getter
public class AttentionUpdateEvent extends ApplicationEvent {
    
    private final String userId;
    private final String targetUserId;
    private final String operation;
    
    public AttentionUpdateEvent(Object source, String userId, String targetUserId, String operation) {
        super(source);
        this.userId = userId;
        this.targetUserId = targetUserId;
        this.operation = operation;
    }
}
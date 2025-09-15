package org.example.easychat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 用户状态变化事件
 */
@Getter
public class UserStatusChangeEvent extends ApplicationEvent {
    
    private final String userId;
    private final String oldStatus;
    private final String newStatus;
    
    public UserStatusChangeEvent(Object source, String userId, String oldStatus, String newStatus) {
        super(source);
        this.userId = userId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }
}
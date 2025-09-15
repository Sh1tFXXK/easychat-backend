package org.example.easychat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 用户离线事件
 */
@Getter
public class UserOfflineEvent extends ApplicationEvent {
    
    private final String userId;
    
    public UserOfflineEvent(Object source, String userId) {
        super(source);
        this.userId = userId;
    }
}
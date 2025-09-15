package org.example.easychat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 用户上线事件
 */
@Getter
public class UserOnlineEvent extends ApplicationEvent {
    
    private final String userId;
    
    public UserOnlineEvent(Object source, String userId) {
        super(source);
        this.userId = userId;
    }
}
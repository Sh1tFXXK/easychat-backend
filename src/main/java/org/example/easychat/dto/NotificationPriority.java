package org.example.easychat.dto;

/**
 * 通知优先级枚举
 */
public enum NotificationPriority {
    /**
     * 低优先级
     */
    LOW(1),
    
    /**
     * 普通优先级
     */
    NORMAL(2),
    
    /**
     * 高优先级
     */
    HIGH(3),
    
    /**
     * 紧急优先级
     */
    URGENT(4);
    
    private final int level;
    
    NotificationPriority(int level) {
        this.level = level;
    }
    
    public int getLevel() {
        return level;
    }
    
    /**
     * 比较优先级
     */
    public boolean isHigherThan(NotificationPriority other) {
        return this.level > other.level;
    }
    
    /**
     * 根据级别获取优先级
     */
    public static NotificationPriority fromLevel(int level) {
        for (NotificationPriority priority : values()) {
            if (priority.level == level) {
                return priority;
            }
        }
        return NORMAL;
    }
}
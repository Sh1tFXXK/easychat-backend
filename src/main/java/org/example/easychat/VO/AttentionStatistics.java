package org.example.easychat.VO;

import lombok.Data;

@Data
public class AttentionStatistics {
    private String userId;
    private Long totalAttentions; // 总关注数
    private Long onlineAttentions; // 在线关注数
    private Long offlineAttentions; // 离线关注数
    private Long todayNewAttentions; // 今日新增关注数
}
package org.example.easychat.VO;

import lombok.Data;

import java.util.List;

@Data
public class BatchOperationResult {
    private int totalCount;
    private int successCount;
    private int failureCount;
    private List<OperationResult> results;
    
    // 兼容性方法
    public int getFailCount() {
        return failureCount;
    }
    
    public void setFailCount(int failCount) {
        this.failureCount = failCount;
    }
    
    @Data
    public static class OperationResult {
        private String targetUserId;
        private boolean success;
        private String message;
        private Object data; // 成功时可能包含创建的对象信息
        
        // 构造函数
        public OperationResult() {}
        
        public OperationResult(String targetUserId, boolean success, String message) {
            this.targetUserId = targetUserId;
            this.success = success;
            this.message = message;
        }
        
        public OperationResult(String targetUserId, boolean success, Object data) {
            this.targetUserId = targetUserId;
            this.success = success;
            this.data = data;
        }
    }
}
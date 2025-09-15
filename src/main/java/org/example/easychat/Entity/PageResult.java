package org.example.easychat.Entity;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private List<T> records;
    private long current;
    private long pageSize;
    private long total;
    private long pages;
    
    public PageResult() {
    }
    
    public PageResult(List<T> records, long total, long current, long pageSize) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.pageSize = pageSize;
        this.pages = (total + pageSize - 1) / pageSize; // 计算总页数
    }
    
    public boolean hasNext() {
        return current < pages;
    }
    
    public boolean hasPrevious() {
        return current > 1;
    }
}

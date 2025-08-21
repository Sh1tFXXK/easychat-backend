package org.example.easychat.Entity;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PageResult {
    private List<ChatHistory> records;
    private int current;
    private int pageSize;
    private int total;
}

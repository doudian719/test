package com.joe.task.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventInfo {
    private String name;
    private String namespace;
    private String type;  // Normal or Warning
    private String reason;
    private String message;
    private String involvedObject;  // 关联对象
    private String creationTimestamp;
    @NonNull
    private String lastTimestamp;
    private Integer count;  // 事件发生次数
} 
package com.joe.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HasuraRemoteSchemaInfo {
    private String name;
    private String url;
    private Instant lastUpdated; // 最后更新时间
    private Instant createdAt;   // 创建时间
    private String status;       // 状态，例如"Active"、"Failed"等
    private String errorMessage; // 若状态为失败，存储错误信息
    private boolean consistent;  // 是否与数据库一致
    private Object metadata;     // 存储原始元数据
} 
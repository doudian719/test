package com.joe.task.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NamespaceInfo {
    private String name;
    private String creationTimestamp;
    private String status;
}

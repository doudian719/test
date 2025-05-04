package com.joe.task.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigMapInfo {
    private String name;
    private String namespace;
    private String creationTimestamp;
    private Map<String, String> data;
}
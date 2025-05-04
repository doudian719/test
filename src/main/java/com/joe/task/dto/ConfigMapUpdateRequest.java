package com.joe.task.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ConfigMapUpdateRequest {
    private String env;
    private String namespace;
    private String name;
    private Map<String, String> data;
} 
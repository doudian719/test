package com.joe.task.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateSchemaDto {
    private String pluginNamespace;
    private String functionName;
    private String prefix;
    private String rootNamespace;
} 
package com.joe.task.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class HasuraRequestDto {
    private String type;
    private Map<String, Object> args;
} 
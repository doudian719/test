package com.joe.task.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class RichNamespaceInfo {
    private final String name;
    private final Instant creationTimestamp;
    private final String phase;
    private String status;
    private final Map<String, String> labels;
    private final Map<String, String> annotations;
    private String env;

    public RichNamespaceInfo(String env, String name, Instant creationTimestamp, String phase,
                            Map<String, String> labels, Map<String, String> annotations) {
        this.name = name;
        this.creationTimestamp = creationTimestamp;
        this.phase = phase;
        this.status = phase;
        this.labels = labels;
        this.annotations = annotations;
        this.env = env;
    }
} 
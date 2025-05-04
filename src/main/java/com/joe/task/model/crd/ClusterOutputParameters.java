package com.joe.task.model.crd;

import lombok.Data;

@Data
public class ClusterOutputParameters {
    private String name;
    private String namespace;
    private String defaultTopic;
} 
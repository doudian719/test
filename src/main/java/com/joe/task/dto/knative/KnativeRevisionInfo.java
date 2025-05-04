package com.joe.task.dto.knative;

import lombok.Data;

import java.util.List;

@Data
public class KnativeRevisionInfo {
    private String name;
    private String serviceName;
    private String creationTimestamp;
    private String image;
    private Integer actualReplicas;
    private Integer desiredReplicas;
    private List<String> conditions;
}
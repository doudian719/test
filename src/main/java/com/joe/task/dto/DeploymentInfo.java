package com.joe.task.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentInfo {
    private String name;
    private String namespace;
    private Integer desiredReplicas;
    private Integer readyReplicas;
    private Integer updatedReplicas;
    private String creationTimestamp;
    private String revision;
    private String observedGeneration;
    private boolean isInProgress;
}
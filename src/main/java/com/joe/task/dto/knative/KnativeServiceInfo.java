package com.joe.task.dto.knative;

import lombok.Data;

import java.util.List;

@Data
public class KnativeServiceInfo {
    private String name;
    private String namespace;
    private String status;
    private List<KnativeCondition> conditions;
    private String url;
    private String generation;
    private String latestCreatedRevision;
    private String latestReadyRevision;
    private String observedGeneration;
    
    @Data
    public static class KnativeCondition {
        private String type;
        private String status;
        private String reason;
        private String message;
        private String lastTransitionTime;
    }
}
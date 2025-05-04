package com.joe.task.dto.knative;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TrafficInfo {
    private String revisionName;
    private Long percent;
    private String tag;
    private Boolean latestRevision;
}
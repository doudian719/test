package com.joe.task.model.crd;

import lombok.Data;
import java.util.List;

@Data
public class ClusterFlowParameters {
    private String name;
    private List<String> namespaces;
} 
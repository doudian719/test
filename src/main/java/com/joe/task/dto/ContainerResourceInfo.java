package com.joe.task.dto;

import io.fabric8.kubernetes.api.model.Quantity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class ContainerResourceInfo {
    private String name;
    private Map<String, Quantity> requests;
    private Map<String, Quantity> limits;
}

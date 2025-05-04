package com.joe.task.dto;

import io.fabric8.kubernetes.api.model.ServicePort;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ServiceInfo {
    private String name;
    private String type;
    private String clusterIP;
    private List<ServicePort> ports;
}
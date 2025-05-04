package com.joe.task.util;

import io.fabric8.kubernetes.api.model.*;
import java.util.List;
import java.util.stream.Collectors;

public class KubernetesOutputFormatter {
    
    // Format pod list into kubectl-like table
    public static String formatPodList(PodList podList) {
        StringBuilder sb = new StringBuilder();
        
        // Header
        sb.append(String.format("%-50s %-10s %-20s %-15s %-10s\n", 
                "NAME", "READY", "STATUS", "RESTARTS", "AGE"));
        
        // Rows
        for (Pod pod : podList.getItems()) {
            String name = pod.getMetadata().getName();
            
            // Calculate ready containers
            List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
            int readyCount = 0;
            int totalCount = containerStatuses != null ? containerStatuses.size() : 0;
            if (containerStatuses != null) {
                readyCount = (int) containerStatuses.stream()
                        .filter(ContainerStatus::getReady)
                        .count();
            }
            String ready = readyCount + "/" + totalCount;
            
            // Get status
            String status = pod.getStatus().getPhase();
            if (pod.getStatus().getContainerStatuses() != null && !pod.getStatus().getContainerStatuses().isEmpty()) {
                ContainerStatus containerStatus = pod.getStatus().getContainerStatuses().get(0);
                if (containerStatus.getState().getWaiting() != null) {
                    status = containerStatus.getState().getWaiting().getReason();
                }
            }
            
            // Calculate restarts
            int restarts = 0;
            if (containerStatuses != null) {
                restarts = containerStatuses.stream()
                        .mapToInt(ContainerStatus::getRestartCount)
                        .sum();
            }
            
            // Calculate age
            String age = calculateAge(pod.getMetadata().getCreationTimestamp());
            
            sb.append(String.format("%-50s %-10s %-20s %-15d %-10s\n",
                    name, ready, status, restarts, age));
        }
        
        return sb.toString();
    }
    
    // Format service list into kubectl-like table
    public static String formatServiceList(ServiceList serviceList) {
        StringBuilder sb = new StringBuilder();
        
        // Header
        sb.append(String.format("%-30s %-10s %-15s %-30s %-10s\n",
                "NAME", "TYPE", "CLUSTER-IP", "EXTERNAL-IP", "PORT(S)"));
        
        // Rows
        for (Service service : serviceList.getItems()) {
            String name = service.getMetadata().getName();
            String type = service.getSpec().getType();
            String clusterIP = service.getSpec().getClusterIP();
            String externalIP = service.getSpec().getExternalIPs() != null && !service.getSpec().getExternalIPs().isEmpty() ?
                    String.join(",", service.getSpec().getExternalIPs()) : "<none>";
            
            String ports = service.getSpec().getPorts().stream()
                    .map(port -> port.getPort() + ":" + port.getTargetPort().toString() + "/" + port.getProtocol())
                    .collect(Collectors.joining(","));
            
            sb.append(String.format("%-30s %-10s %-15s %-30s %-10s\n",
                    name, type, clusterIP, externalIP, ports));
        }
        
        return sb.toString();
    }
    
    // Helper method to calculate age
    private static String calculateAge(String timestamp) {
        if (timestamp == null) return "Unknown";
        
        long creationTime = java.time.Instant.parse(timestamp).toEpochMilli();
        long currentTime = System.currentTimeMillis();
        long age = currentTime - creationTime;
        
        long days = age / (24 * 60 * 60 * 1000);
        if (days > 0) {
            return days + "d";
        }
        
        long hours = age / (60 * 60 * 1000);
        if (hours > 0) {
            return hours + "h";
        }
        
        long minutes = age / (60 * 1000);
        return minutes + "m";
    }
} 
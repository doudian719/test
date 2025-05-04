package com.joe.task.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
public class PodInfo {
    private String name;
    private String namespace;
    private String status;
    private String ip;
    private String nodeName;
    private String creationTimestamp;
    private int restartCount;
    private String uptime;
    private Long startTimeMillis;
    private List<ContainerResourceInfo> containers;
    private Long lastRestartTime;
}
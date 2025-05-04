package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class LogService extends BaseService
{
    private final KubernetesClientManager clientManager;

    private static final int MAX_LOG_LINES = 300;

    public LogService(KubernetesClientManager clientManager)
    {
        super(clientManager);
        this.clientManager = clientManager;
    }

    private String[] parseLabelKeyValue(String label) {
        if (label == null || !label.contains("=")) {
            throw new IllegalArgumentException("Label must be in format 'key=value'");
        }
        return label.split("=", 2);
    }

    public String getPodsLogs(String env, String namespace, Optional<String> podName, Optional<String> podLabel, Optional<String> keyword) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Fetching pods in namespace: {} for environment: {}", namespace, env);

        // Validate that either podName or podLabel is present
        if (podName.isEmpty() && podLabel.isEmpty()) {
            throw new IllegalArgumentException("Either podName or podLabel must be provided");
        }

        StringBuilder combinedLogs = new StringBuilder();

        if (podName.isPresent()) {
            Pod pod = client.pods()
                    .inNamespace(namespace)
                    .withName(podName.get())
                    .get();
            
            if (pod != null) {
                List<Container> containers = pod.getSpec().getContainers();
                for (Container container : containers) {
                    String containerName = container.getName();
                    String logs = client.pods()
                            .inNamespace(namespace)
                            .withName(podName.get())
                            .inContainer(containerName)
                            .tailingLines(MAX_LOG_LINES)
                            .getLog();

                    if (keyword.isPresent() && !keyword.get().isEmpty()) {
                        // Filter logs by keyword
                        String[] lines = logs.split("\n");
                        combinedLogs.append("=== Logs from container: ")
                                .append(containerName)
                                .append(" ===\n");
                        for (String line : lines) {
                            if (line.contains(keyword.get())) {
                                combinedLogs.append(line).append("\n");
                            }
                        }
                    } else {
                        combinedLogs.append("=== Logs from container: ")
                                .append(containerName)
                                .append(" ===\n")
                                .append(logs)
                                .append("\n\n");
                    }
                }
            }
        } else {
            // Get logs from pods matching the label
            String[] labelParts = parseLabelKeyValue(podLabel.get());
            log.info("Searching for pods with label key: '{}', value: '{}'", labelParts[0], labelParts[1]);
            
            List<Pod> pods = client.pods()
                    .inNamespace(namespace)
                    .withLabel(labelParts[0], labelParts[1])
                    .list()
                    .getItems();
            
            log.info("Found {} pods matching the label", pods.size());
            
            // 如果没有找到pod，尝试使用标签选择器
            if (pods.isEmpty()) {
                log.info("Trying alternative label selector approach");
                pods = client.pods()
                    .inNamespace(namespace)
                    .withLabelSelector(podLabel.get())
                    .list()
                    .getItems();
                log.info("Found {} pods using label selector", pods.size());
            }

            pods.forEach(pod -> {
                log.info("Processing pod: {}", pod.getMetadata().getName());
                List<Container> containers = pod.getSpec().getContainers();
                for (Container container : containers) {
                    String containerName = container.getName();
                    log.info("Getting logs for container: {}", containerName);
                    try {
                        String podLogs = client.pods()
                                .inNamespace(namespace)
                                .withName(pod.getMetadata().getName())
                                .inContainer(containerName)
                                .tailingLines(MAX_LOG_LINES)
                                .getLog();

                        if (keyword.isPresent() && !keyword.get().isEmpty()) {
                            // Filter logs by keyword
                            String[] lines = podLogs.split("\n");
                            combinedLogs.append("=== Logs from pod: ")
                                    .append(pod.getMetadata().getName())
                                    .append(", container: ")
                                    .append(containerName)
                                    .append(" ===\n");
                            for (String line : lines) {
                                if (line.contains(keyword.get())) {
                                    combinedLogs.append(line).append("\n");
                                }
                            }
                        } else {
                            combinedLogs.append("=== Logs from pod: ")
                                    .append(pod.getMetadata().getName())
                                    .append(", container: ")
                                    .append(containerName)
                                    .append(" ===\n")
                                    .append(podLogs)
                                    .append("\n\n");
                        }
                    } catch (Exception e) {
                        log.error("Error getting logs for pod {} container {}: {}", 
                            pod.getMetadata().getName(), containerName, e.getMessage());
                        combinedLogs.append("=== Error getting logs for pod: ")
                                .append(pod.getMetadata().getName())
                                .append(", container: ")
                                .append(containerName)
                                .append(" ===\n")
                                .append(e.getMessage())
                                .append("\n\n");
                    }
                }
            });
        }

        return combinedLogs.length() > 0 ? combinedLogs.toString() : "No logs found matching the criteria";
    }


    /**
     * 获取指定 Pod 的所有 Container 日志并打包成 zip 文件
     */
    public File getPodLogsAsZip(String env, String namespace, String podName) throws IOException {
        KubernetesClient client = clientManager.getClient(env);
        Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
        
        // 创建临时zip文件
        File zipFile = File.createTempFile(env + "-" + namespace + "-" + podName + "-logs", ".zip");
        
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            
            // 遍历Pod的所有容器
            pod.getSpec().getContainers().forEach(container -> {
                String containerName = container.getName();
                try {
                    // 使用非流式API获取日志
                    String logs = client.pods()
                        .inNamespace(namespace)
                        .withName(podName)
                        .inContainer(containerName)
                        .tailingLines(10000)  // 限制日志行数
                        .getLog();
                    
                    // 将日志写入zip
                    ZipEntry zipEntry = new ZipEntry(containerName + ".log");
                    zipOut.putNextEntry(zipEntry);
                    zipOut.write(logs.getBytes());
                    zipOut.closeEntry();
                    
                } catch (IOException e) {
                    log.error("Error writing logs for container: " + containerName, e);
                }
            });
        }
        
        return zipFile;
    }

    public void streamPodLogsAsZip(String env, 
                                  String namespace, 
                                  String podName, 
                                  OutputStream outputStream) throws IOException {
        KubernetesClient client = clientManager.getClient(env);
        Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
        
        if (pod == null) {
            throw new IOException("Pod not found: " + podName);
        }
        
        try (ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(outputStream))) {
            // 遍历Pod的所有容器
            for (Container container : pod.getSpec().getContainers()) {
                String containerName = container.getName();
                log.info("Processing logs for container: {}", containerName);
                
                try {
                    String logs = client.pods()
                        .inNamespace(namespace)
                        .withName(podName)
                        .inContainer(containerName)
                        // .sinceSeconds(3600)  // 获取最近1小时的日志
                        .tailingLines(10000) // 限制日志行数
                        .getLog();
                    
                    // 创建zip条目
                    ZipEntry zipEntry = new ZipEntry(containerName + ".log");
                    zipOut.putNextEntry(zipEntry);
                    
                    // 写入日志
                    byte[] logBytes = logs.getBytes();
                    zipOut.write(logBytes, 0, logBytes.length);
                    zipOut.closeEntry();
                    
                    log.info("Successfully wrote logs for container: {} (size: {} bytes)", 
                        containerName, logBytes.length);
                    
                } catch (Exception e) {
                    log.error("Error getting logs for container: {}", containerName, e);
                    // 创建错误日志条目
                    ZipEntry errorEntry = new ZipEntry(containerName + "_error.log");
                    zipOut.putNextEntry(errorEntry);
                    String errorMessage = "Error getting logs: " + e.getMessage();
                    zipOut.write(errorMessage.getBytes());
                    zipOut.closeEntry();
                }
            }
        } catch (Exception e) {
            log.error("Error creating zip file", e);
            throw new IOException("Failed to create zip file: " + e.getMessage(), e);
        }
    }

    /**
     * 获取指定 Pod 的 Init Container 日志
     */
    public String getInitContainerLogs(String env, String namespace, String podName) {
        KubernetesClient client = clientManager.getClient(env);
        Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
        
        if (pod == null) {
            throw new RuntimeException("Pod not found: " + podName);
        }

        StringBuilder logs = new StringBuilder();
        logs.append("=== Pod Status: ").append(pod.getStatus().getPhase()).append(" ===\n\n");
        
        // 获取 Init Containers 的日志
        if (pod.getSpec().getInitContainers() != null) {
            for (Container initContainer : pod.getSpec().getInitContainers()) {
                String containerName = initContainer.getName();
                
                // 获取容器状态
                ContainerStatus containerStatus = pod.getStatus().getInitContainerStatuses().stream()
                    .filter(status -> status.getName().equals(containerName))
                    .findFirst()
                    .orElse(null);
                
                if (containerStatus != null) {
                    logs.append("=== Init Container Status: ").append(containerName).append(" ===\n");
                    logs.append("State: ").append(containerStatus.getState()).append("\n");
                    if (containerStatus.getState().getTerminated() != null) {
                        logs.append("Exit Code: ").append(containerStatus.getState().getTerminated().getExitCode()).append("\n");
                        logs.append("Reason: ").append(containerStatus.getState().getTerminated().getReason()).append("\n");
                        logs.append("Message: ").append(containerStatus.getState().getTerminated().getMessage()).append("\n");
                    }
                    logs.append("\n");
                }
                
                try {
                    String containerLogs = client.pods()
                        .inNamespace(namespace)
                        .withName(podName)
                        .inContainer(containerName)
                        .tailingLines(10000)
                        .getLog();
                    
                    logs.append("=== Init Container Logs: ")
                        .append(containerName)
                        .append(" ===\n")
                        .append(containerLogs)
                        .append("\n\n");
                } catch (Exception e) {
                    logs.append("=== Error getting logs for init container: ")
                        .append(containerName)
                        .append(" ===\n")
                        .append(e.getMessage())
                        .append("\n\n");
                }
            }
        } else {
            logs.append("No init containers found for this pod.\n");
        }

        return logs.toString();
    }

    /**
     * 从事件消息中提取时间
     */
    private String extractTimeFromMessage(String message) {
        if (message == null) return "";
        // 匹配形如 "at Sun Mar 9 00:07:12 AWST 2025" 的时间格式
        int atIndex = message.indexOf(" at ");
        if (atIndex >= 0) {
            return message.substring(atIndex + 4);
        }
        return "";
    }

    /**
     * 获取指定 Pod 的事件信息
     */
    public String getPodEvents(String env, String namespace, String podName) {
        KubernetesClient client = clientManager.getClient(env);
        
        StringBuilder events = new StringBuilder();
        events.append("=== Pod Events ===\n\n");
        
        try {
            client.v1().events()
                .inNamespace(namespace)
                .withField("involvedObject.name", podName)
                .withField("involvedObject.kind", "Pod")
                .list()
                .getItems()
                .stream()
                .sorted((e1, e2) -> {
                    // 从消息中提取时间进行排序
                    String time1 = extractTimeFromMessage(e1.getMessage());
                    String time2 = extractTimeFromMessage(e2.getMessage());
                    
                    if (time1.isEmpty() && time2.isEmpty()) {
                        return 0;
                    }
                    if (time1.isEmpty()) {
                        return 1;
                    }
                    if (time2.isEmpty()) {
                        return -1;
                    }
                    // 倒序排序，最新的在前面
                    return time2.compareTo(time1);
                })
                .forEach(event -> {
                    // 根据事件类型添加不同的样式
                    String eventType = event.getType();
                    String typeStyle;
                    if ("Warning".equalsIgnoreCase(eventType)) {
                        typeStyle = "[WARNING]";
                        events.append("<div style='margin-bottom: 10px; padding: 8px; border-left: 4px solid #ff9900; background-color: #fff9f0;'>");
                    } else if ("Error".equalsIgnoreCase(eventType)) {
                        typeStyle = "[ERROR]";
                        events.append("<div style='margin-bottom: 10px; padding: 8px; border-left: 4px solid #ed4014; background-color: #ffefef;'>");
                    } else {
                        typeStyle = "[NORMAL]";
                        events.append("<div style='margin-bottom: 10px; padding: 8px; border-left: 4px solid #19be6b; background-color: #f0fff0;'>");
                    }

                    // 从消息中提取时间
                    String timestamp = "N/A";
                    String timeFromMessage = extractTimeFromMessage(event.getMessage());
                    if (!timeFromMessage.isEmpty()) {
                        timestamp = timeFromMessage;
                    }

                    events.append(String.format("<div style='margin-bottom: 5px;'><span style='color: #666666;'>%s</span> ", timestamp))
                          .append(String.format("<span style='font-weight: bold;'>%s</span> ", typeStyle))
                          .append(String.format("<span style='color: #2d8cf0;'>%s</span></div>", event.getReason()))
                          .append(String.format("<div style='color: #515a6e; margin-left: 10px;'>%s</div>", event.getMessage()))
                          .append("</div>");
                });

            if (events.toString().equals("=== Pod Events ===\n\n")) {
                events.append("<div style='color: #666666; font-style: italic;'>No events found for this pod.</div>");
            }
        } catch (Exception e) {
            events.append("<div style='color: #ed4014; font-weight: bold; padding: 8px; border: 1px solid #ed4014; background-color: #ffefef;'>")
                .append("Error getting pod events: ")
                .append(e.getMessage())
                .append("</div>");
        }

        return events.toString();
    }

    /**
     * 获取指定 Pod 的详细描述
     */
    public String getPodDescription(String env, String namespace, String podName) {
        KubernetesClient client = clientManager.getClient(env);
        Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
        
        if (pod == null) {
            throw new RuntimeException("Pod not found: " + podName);
        }

        StringBuilder description = new StringBuilder();
        description.append("=== Pod Description ===\n");
        
        // 添加基本信息
        description.append("Name: ").append(pod.getMetadata().getName()).append("\n");
        description.append("Namespace: ").append(pod.getMetadata().getNamespace()).append("\n");
        description.append("Status: ").append(pod.getStatus().getPhase()).append("\n\n");

        // 添加 Init Container 状态
        if (pod.getStatus().getInitContainerStatuses() != null) {
            description.append("=== Init Container Statuses ===\n");
            pod.getStatus().getInitContainerStatuses().forEach(status -> {
                description.append("Container: ").append(status.getName()).append("\n");
                description.append("State: ").append(status.getState()).append("\n");
                if (status.getState().getTerminated() != null) {
                    description.append("Exit Code: ").append(status.getState().getTerminated().getExitCode()).append("\n");
                    description.append("Reason: ").append(status.getState().getTerminated().getReason()).append("\n");
                    description.append("Message: ").append(status.getState().getTerminated().getMessage()).append("\n");
                }
                description.append("\n");
            });
        }

        return description.toString();
    }

    /**
     * 获取指定 Pod 的所有相关信息（包括 Init Container 日志、事件和描述）
     */
    public String getAllPodInfo(String env, String namespace, String podName) {
        StringBuilder allInfo = new StringBuilder();
        
        // 获取 Pod 描述
        allInfo.append(getPodDescription(env, namespace, podName));
        
        // 获取 Pod 事件
        allInfo.append(getPodEvents(env, namespace, podName));
        
        // 获取 Init Container 日志
        allInfo.append(getInitContainerLogs(env, namespace, podName));
        
        return allInfo.toString();
    }

}

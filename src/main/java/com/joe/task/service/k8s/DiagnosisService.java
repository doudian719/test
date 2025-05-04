package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class DiagnosisService extends BaseService {
    
    private final KubernetesClientManager clientManager;

    public DiagnosisService(KubernetesClientManager clientManager) {
        super(clientManager);
        this.clientManager = clientManager;
    }

    /**
     * Pod 故障诊断
     */
    public Map<String, Object> diagnosePod(String env, String namespace, String podName) {
        KubernetesClient client = clientManager.getClient(env);
        Map<String, Object> diagnosis = new HashMap<>();
        
        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                diagnosis.put("status", "error");
                diagnosis.put("message", "Pod not found");
                return diagnosis;
            }

            // 1. 收集Pod基本信息
            diagnosis.put("podBasicInfo", collectPodBasicInfo(pod));

            // 2. 收集资源使用情况
            diagnosis.put("resourceUsage", collectResourceUsage(client, pod));

            // 3. 基本状态检查
            diagnosis.put("podStatus", analyzePodStatus(pod));

            // 4. 资源使用检查
            diagnosis.put("resourceStatus", analyzeResources(pod));

            // 5. 容器状态检查
            diagnosis.put("containerStatus", analyzeContainers(pod));

            // 6. 网络检查
            diagnosis.put("networkStatus", analyzeNetwork(pod));

            // 7. 存储检查
            diagnosis.put("volumeStatus", analyzeVolumes(pod));

            // 8. 节点状态检查
            diagnosis.put("nodeStatus", analyzeNode(client, pod));

            diagnosis.put("status", "success");
        } catch (Exception e) {
            log.error("Error diagnosing pod: {}", e.getMessage(), e);
            diagnosis.put("status", "error");
            diagnosis.put("message", "Diagnosis failed: " + e.getMessage());
        }

        return diagnosis;
    }

    private Map<String, Object> analyzePodStatus(Pod pod) {
        Map<String, Object> status = new HashMap<>();
        
        // 检查Pod阶段
        String phase = pod.getStatus().getPhase();
        status.put("phase", phase);
        
        // 检查Pod条件
        List<Map<String, String>> conditions = new ArrayList<>();
        if (pod.getStatus().getConditions() != null) {
            pod.getStatus().getConditions().forEach(condition -> {
                Map<String, String> conditionMap = new HashMap<>();
                conditionMap.put("type", condition.getType());
                conditionMap.put("status", condition.getStatus());
                conditionMap.put("reason", condition.getReason());
                conditionMap.put("message", condition.getMessage());
                conditions.add(conditionMap);
            });
        }
        status.put("conditions", conditions);

        // 添加诊断建议
        List<String> suggestions = new ArrayList<>();
        if ("Pending".equals(phase)) {
            suggestions.add("Pod处于Pending状态，可能原因：资源不足、镜像拉取失败、PVC未就绪等");
        } else if ("Failed".equals(phase)) {
            suggestions.add("Pod处于Failed状态，请检查容器日志和事件");
        }
        status.put("suggestions", suggestions);

        return status;
    }

    private Map<String, Object> analyzeResources(Pod pod) {
        Map<String, Object> resources = new HashMap<>();
        List<String> suggestions = new ArrayList<>();
        List<Map<String, Object>> containerResources = new ArrayList<>();

        // 收集缺少资源限制和请求的容器
        List<String> containersWithoutLimits = new ArrayList<>();
        List<String> containersWithoutRequests = new ArrayList<>();

        // 检查资源请求和限制
        pod.getSpec().getContainers().forEach(container -> {
            ResourceRequirements reqs = container.getResources();
            Map<String, Object> containerResource = new HashMap<>();
            containerResource.put("name", container.getName());
            
            // 提取CPU和内存请求
            if (reqs.getRequests() != null && !reqs.getRequests().isEmpty()) {
                containerResource.put("cpuRequest", reqs.getRequests().getOrDefault("cpu", null));
                containerResource.put("memoryRequest", reqs.getRequests().getOrDefault("memory", null));
            } else {
                containerResource.put("cpuRequest", null);
                containerResource.put("memoryRequest", null);
                containersWithoutRequests.add(container.getName());
            }
            
            // 提取CPU和内存限制
            if (reqs.getLimits() != null && !reqs.getLimits().isEmpty()) {
                containerResource.put("cpuLimit", reqs.getLimits().getOrDefault("cpu", null));
                containerResource.put("memoryLimit", reqs.getLimits().getOrDefault("memory", null));
            } else {
                containerResource.put("cpuLimit", null);
                containerResource.put("memoryLimit", null);
                containersWithoutLimits.add(container.getName());
            }
            
            // 为每个容器添加当前使用量占位符，后续由前端JS填充
            containerResource.put("cpuUsage", "N/A");
            containerResource.put("memoryUsage", "N/A");
            
            containerResources.add(containerResource);
        });

        // 生成针对资源限制的建议
        if (!containersWithoutLimits.isEmpty()) {
            if (containersWithoutLimits.size() == 1) {
                suggestions.add("容器 " + containersWithoutLimits.get(0) + " 未设置资源限制，建议设置以防止资源过度使用");
            } else {
                // 多个容器，为了兼容前端现有逻辑，仍然每个容器生成一条
                for (String containerName : containersWithoutLimits) {
                    suggestions.add("容器 " + containerName + " 未设置资源限制，建议设置以防止资源过度使用");
                }
            }
        }

        // 生成针对资源请求的建议
        if (!containersWithoutRequests.isEmpty()) {
            if (containersWithoutRequests.size() == 1) {
                suggestions.add("容器 " + containersWithoutRequests.get(0) + " 未设置资源请求，可能影响调度决策");
            } else {
                // 多个容器，为了兼容前端现有逻辑，仍然每个容器生成一条
                for (String containerName : containersWithoutRequests) {
                    suggestions.add("容器 " + containerName + " 未设置资源请求，可能影响调度决策");
                }
            }
        }

        resources.put("suggestions", suggestions);
        resources.put("containerResources", containerResources);
        return resources;
    }

    private Map<String, Object> analyzeContainers(Pod pod) {
        Map<String, Object> containers = new HashMap<>();
        List<Map<String, Object>> containerStatuses = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        // 检查容器状态
        if (pod.getStatus().getContainerStatuses() != null) {
            pod.getStatus().getContainerStatuses().forEach(status -> {
                Map<String, Object> containerStatus = new HashMap<>();
                containerStatus.put("name", status.getName());
                containerStatus.put("ready", status.getReady());
                containerStatus.put("restartCount", status.getRestartCount());

                if (status.getState().getWaiting() != null) {
                    containerStatus.put("state", "Waiting");
                    containerStatus.put("reason", status.getState().getWaiting().getReason());
                    String reason = status.getState().getWaiting().getReason();
                    if ("CrashLoopBackOff".equals(reason)) {
                        suggestions.add("容器 " + status.getName() + " 正在循环崩溃，请检查容器日志");
                    } else if ("ImagePullBackOff".equals(reason)) {
                        suggestions.add("容器 " + status.getName() + " 镜像拉取失败，请检查镜像名称和仓库认证");
                    }
                }

                if (status.getRestartCount() > 3) {
                    suggestions.add("容器 " + status.getName() + " 重启次数过多，可能存在稳定性问题");
                }

                containerStatuses.add(containerStatus);
            });
        }

        containers.put("containerStatuses", containerStatuses);
        containers.put("suggestions", suggestions);
        return containers;
    }

    private Map<String, Object> analyzeNetwork(Pod pod) {
        Map<String, Object> network = new HashMap<>();
        List<String> suggestions = new ArrayList<>();

        // 检查网络状态
        String podIP = pod.getStatus().getPodIP();
        if (podIP == null || podIP.isEmpty()) {
            suggestions.add("Pod未分配IP地址，可能存在网络配置问题");
        }

        // 检查Service关联
        if (pod.getMetadata().getLabels() == null || pod.getMetadata().getLabels().isEmpty()) {
            suggestions.add("Pod没有标签，可能无法被Service选择");
        }

        network.put("podIP", podIP);
        network.put("suggestions", suggestions);
        return network;
    }

    private Map<String, Object> analyzeVolumes(Pod pod) {
        Map<String, Object> volumes = new HashMap<>();
        List<String> suggestions = new ArrayList<>();

        // 检查存储卷
        if (pod.getSpec().getVolumes() != null) {
            pod.getSpec().getVolumes().forEach(volume -> {
                if (volume.getPersistentVolumeClaim() != null) {
                    suggestions.add("检查PVC " + volume.getPersistentVolumeClaim().getClaimName() + " 是否正常绑定");
                }
            });
        }

        volumes.put("suggestions", suggestions);
        return volumes;
    }

    private Map<String, Object> analyzeNode(KubernetesClient client, Pod pod) {
        Map<String, Object> nodeStatus = new HashMap<>();
        List<String> suggestions = new ArrayList<>();

        String nodeName = pod.getSpec().getNodeName();
        if (nodeName != null) {
            Node node = client.nodes().withName(nodeName).get();
            if (node != null) {
                // 检查节点状态
                boolean nodeReady = node.getStatus().getConditions().stream()
                    .anyMatch(condition -> "Ready".equals(condition.getType()) 
                            && "True".equals(condition.getStatus()));
                
                if (!nodeReady) {
                    suggestions.add("Pod所在节点状态异常，可能影响Pod运行");
                }

                // 检查节点资源压力
                boolean nodeHasPressure = node.getStatus().getConditions().stream()
                    .anyMatch(condition -> 
                        ("DiskPressure".equals(condition.getType()) 
                        || "MemoryPressure".equals(condition.getType())
                        || "PIDPressure".equals(condition.getType()))
                        && "True".equals(condition.getStatus()));
                
                if (nodeHasPressure) {
                    suggestions.add("节点存在资源压力，可能影响Pod性能");
                }
            }
        }

        nodeStatus.put("nodeName", nodeName);
        nodeStatus.put("suggestions", suggestions);
        return nodeStatus;
    }

    /**
     * 收集Pod基本信息
     */
    private Map<String, Object> collectPodBasicInfo(Pod pod) {
        Map<String, Object> basicInfo = new HashMap<>();
        
        // 创建时间
        basicInfo.put("creationTimestamp", pod.getMetadata().getCreationTimestamp());
        
        // QoS等级
        basicInfo.put("qosClass", pod.getStatus().getQosClass());
        
        // 标签
        if (pod.getMetadata().getLabels() != null) {
            basicInfo.put("labels", pod.getMetadata().getLabels());
        } else {
            basicInfo.put("labels", new HashMap<>());
        }
        
        // 注解
        if (pod.getMetadata().getAnnotations() != null) {
            basicInfo.put("annotations", pod.getMetadata().getAnnotations());
        } else {
            basicInfo.put("annotations", new HashMap<>());
        }
        
        return basicInfo;
    }
    
    /**
     * 收集资源使用情况
     * 注意：这里只能返回请求和限制的静态数据，
     * 获取实时资源使用需要对接 Metrics API
     */
    private Map<String, Object> collectResourceUsage(KubernetesClient client, Pod pod) {
        Map<String, Object> resourceUsage = new HashMap<>();
        
        // 1. CPU资源
        Map<String, Object> cpu = new HashMap<>();
        
        // 2. 内存资源
        Map<String, Object> memory = new HashMap<>();
        
        // 收集所有容器的资源配置
        for (Container container : pod.getSpec().getContainers()) {
            ResourceRequirements resources = container.getResources();
            
            // CPU请求和限制
            if (resources.getRequests() != null && resources.getRequests().containsKey("cpu")) {
                String cpuRequest = resources.getRequests().get("cpu").toString();
                cpu.put("request", cpu.getOrDefault("request", "0") + " + " + cpuRequest);
            }
            
            if (resources.getLimits() != null && resources.getLimits().containsKey("cpu")) {
                String cpuLimit = resources.getLimits().get("cpu").toString();
                cpu.put("limit", cpu.getOrDefault("limit", "0") + " + " + cpuLimit);
            }
            
            // 内存请求和限制
            if (resources.getRequests() != null && resources.getRequests().containsKey("memory")) {
                String memRequest = resources.getRequests().get("memory").toString();
                memory.put("request", memory.getOrDefault("request", "0") + " + " + memRequest);
            }
            
            if (resources.getLimits() != null && resources.getLimits().containsKey("memory")) {
                String memLimit = resources.getLimits().get("memory").toString();
                memory.put("limit", memory.getOrDefault("limit", "0") + " + " + memLimit);
            }
        }
        
        // 设置默认值和模拟当前使用
        if (!cpu.containsKey("request")) cpu.put("request", "0");
        if (!cpu.containsKey("limit")) cpu.put("limit", "0");
        cpu.put("current", "N/A"); // 实际需要对接Metrics API
        cpu.put("usagePercentage", 0);
        
        if (!memory.containsKey("request")) memory.put("request", "0");
        if (!memory.containsKey("limit")) memory.put("limit", "0");
        memory.put("current", "N/A"); // 实际需要对接Metrics API
        memory.put("usagePercentage", 0);
        
        resourceUsage.put("cpu", cpu);
        resourceUsage.put("memory", memory);
        
        return resourceUsage;
    }
} 
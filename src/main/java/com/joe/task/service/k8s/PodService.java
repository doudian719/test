package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import com.joe.task.dto.ContainerResourceInfo;
import com.joe.task.dto.PodInfo;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PodService extends BaseService
{
    private final KubernetesClientManager clientManager;

    public PodService(KubernetesClientManager clientManager)
    {
        super(clientManager);
        this.clientManager = clientManager;
    }

    public List<PodInfo> getPodsInNamespace(String env, String namespace, Optional<String> status, Optional<String> name) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Fetching pods in namespace: {} for environment: {}", namespace, env);
        try {
            List<Pod> pods = client.pods()
                    .inNamespace(namespace)
                    .list()
                    .getItems();

            List<PodInfo> podInfoList = pods.stream()
                    .map(this::convertToPodInfo)
                    .filter(podInfo -> !status.isPresent() || status.map(s -> s.equalsIgnoreCase(podInfo.getStatus())).orElse(true))
                    .filter(podInfo -> !name.isPresent() || name.map(s -> StringUtils.containsIgnoreCase(podInfo.getName(), name.get())).orElse(true))
                    .sorted(Comparator.comparing(PodInfo::getStartTimeMillis, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());

            return podInfoList;
        } catch (Exception e) {
            log.error("Error fetching pods in namespace: {} for environment: {}", namespace, env, e);
            throw new RuntimeException("Failed to fetch pods", e);
        }
    }

    private PodInfo convertToPodInfo(Pod pod) {
        PodInfo podInfo = new PodInfo();
        podInfo.setName(pod.getMetadata().getName());
        podInfo.setNamespace(pod.getMetadata().getNamespace());

        // Get container statuses
        List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
        if (containerStatuses != null && !containerStatuses.isEmpty()) {
            ContainerStatus containerStatus = containerStatuses.get(0);
            if (containerStatus.getState().getWaiting() != null) {
                // If container is waiting, use the waiting reason as status
                podInfo.setStatus(containerStatus.getState().getWaiting().getReason());
            } else if (containerStatus.getState().getTerminated() != null) {
                // If container is terminated, use the termination reason
                podInfo.setStatus(containerStatus.getState().getTerminated().getReason());
            } else if (containerStatus.getState().getRunning() != null) {
                // If container is running, use the pod phase
                podInfo.setStatus(pod.getStatus().getPhase());
            }
        } else {
            // Fallback to pod phase if no container status available
            podInfo.setStatus(pod.getStatus().getPhase());
        }

        podInfo.setIp(pod.getStatus().getPodIP());
        podInfo.setNodeName(pod.getSpec().getNodeName());
        podInfo.setCreationTimestamp(pod.getMetadata().getCreationTimestamp());

        // Calculate restart count
        int restartCount = pod.getStatus().getContainerStatuses()
                .stream()
                .mapToInt(ContainerStatus::getRestartCount)
                .sum();
        podInfo.setRestartCount(restartCount);

        // Calculate uptime and startTimeMillis
        String startTimeStr = pod.getStatus().getStartTime();
        if (startTimeStr != null) {
            Instant instant = Instant.parse(startTimeStr);
            LocalDateTime startTime = LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());

            // Set the startTimeMillis for sorting
            podInfo.setStartTimeMillis(instant.toEpochMilli());

            // Calculate and set the human-readable uptime
            java.time.Duration uptime = java.time.Duration.between(startTime, LocalDateTime.now());
            podInfo.setUptime(formatDuration(uptime));
        }

        // Calculate last restart time
        Instant lastRestart = pod.getStatus().getContainerStatuses()
            .stream()
            .map(ContainerStatus::getLastState)
            .filter(Objects::nonNull)
            .map(state -> {
                if (state.getTerminated() != null) {
                    return state.getTerminated().getFinishedAt();
                } else if (state.getWaiting() != null) {
                    return state.getWaiting().getMessage(); // 可能需要调整
                }
                return null;
            })
            .filter(Objects::nonNull)
            .map(Instant::parse)
            .max(Comparator.naturalOrder())
            .orElse(null);

        if (lastRestart != null) {
            podInfo.setLastRestartTime(lastRestart.toEpochMilli());
        }

        // Get container resources
        List<ContainerResourceInfo> containers = pod.getSpec().getContainers()
                .stream()
                .map(container -> new ContainerResourceInfo(
                        container.getName(),
                        container.getResources().getRequests(),
                        container.getResources().getLimits()))
                .collect(Collectors.toList());
        podInfo.setContainers(containers);

        return podInfo;
    }

    /**
     * Delete a pod in the specified namespace
     * @param env Environment name
     * @param namespace Namespace name
     * @param podName Pod name
     */
    public void deletePod(String env, String namespace, String podName) {
        KubernetesClient client = clientManager.getClient(env);
        log.info("Deleting pod: {} in namespace: {} for environment: {}", podName, namespace, env);
        
        try {
            client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .delete();
            
            log.info("Successfully deleted pod: {} in namespace: {}", podName, namespace);
        } catch (Exception e) {
            log.error("Error deleting pod: {} in namespace: {} for environment: {}", 
                    podName, namespace, env, e);
            throw new RuntimeException("Failed to delete pod: " + e.getMessage(), e);
        }
    }
}

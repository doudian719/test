package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import com.joe.task.dto.NamespaceInfo;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BaseService
{
    private final KubernetesClientManager clientManager;

    @Autowired
    public BaseService(KubernetesClientManager clientManager)
    {
        this.clientManager = clientManager;
    }

    public NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> getNamespaces(String env) {
        return clientManager.getClient(env).namespaces();
    }

    static boolean isValidExperienceApi(GenericKubernetesResource resource)
    {
        String serviceName = resource.getMetadata().getName();
        return (StringUtils.containsIgnoreCase(serviceName, "exp")
                || StringUtils.containsIgnoreCase(serviceName, "plugin"))
                && (!StringUtils.endsWithIgnoreCase(serviceName, "-ui"));
    }

    boolean checkIfRunningRecently(GenericKubernetesResource resource, Instant oneHourAgo)
    {
        Object statusObj = resource.get("status");
        if (!(statusObj instanceof java.util.Map<?, ?> statusMap)) {
            return false;
        }
        Object conditionsObj = statusMap.get("conditions");
        if (!(conditionsObj instanceof List)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        List<Object> conditions = (List<Object>) conditionsObj;
        for (Object c : conditions) {
            if (c instanceof java.util.Map<?, ?> condition) {
                Object type = condition.get("type");
                Object condStatus = condition.get("status");
                if ("Ready".equals(type) && "True".equals(condStatus)) {
                    Object lastTransitionTime = condition.get("lastTransitionTime");

                    if(lastTransitionTime instanceof String) {
                        Instant lastTransition = Instant.parse((String) lastTransitionTime);
                        return lastTransition.isAfter(oneHourAgo);
                    }

                    return false;
                }
            }
        }
        return false;
    }

    @Cacheable(value = "namespaces", key = "#env")
    public List<NamespaceInfo> getAllNamespaces(String env) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Fetching all namespaces for environment: {}", env);
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                log.info("Attempt {} to fetch namespaces", retryCount + 1);
                log.info("Current context: {}", client.getConfiguration().getCurrentContext());
                log.info("Master URL: {}", client.getConfiguration().getMasterUrl());

                return client.namespaces()
                        .list()
                        .getItems()
                        .stream()
                        .map(this::convertToNamespaceInfo)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                retryCount++;
                if (retryCount == maxRetries) {
                    log.error("Error fetching namespaces after {} attempts", maxRetries, e);
                    throw new RuntimeException("Failed to fetch namespaces", e);
                }
                log.warn("Error fetching namespaces (attempt {}), retrying...", retryCount, e);
                try {
                    Thread.sleep(1000 * retryCount); // 增加重试间隔
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying", ie);
                }
            }
        }
        throw new RuntimeException("Failed to fetch namespaces after " + maxRetries + " attempts");
    }

    protected NamespaceInfo convertToNamespaceInfo(Namespace ns) {
        return new NamespaceInfo(
                ns.getMetadata().getName(),
                ns.getMetadata().getCreationTimestamp(),
                ns.getStatus().getPhase());
    }

    protected String formatDuration(java.time.Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m");

        return sb.toString().trim();
    }

    protected String calculateAge(String creationTimestamp) {
        if (creationTimestamp == null) return "";

        try {
            Date created = new Date(creationTimestamp);
            Date now = new Date();
            long diffInMillies = now.getTime() - created.getTime();

            long days = diffInMillies / (24 * 60 * 60 * 1000);
            if (days > 0) return days + "d";

            long hours = diffInMillies / (60 * 60 * 1000);
            if (hours > 0) return hours + "h";

            long minutes = diffInMillies / (60 * 1000);
            if (minutes > 0) return minutes + "m";

            return "Just now";
        } catch (Exception e) {
            return "";
        }
    }
}

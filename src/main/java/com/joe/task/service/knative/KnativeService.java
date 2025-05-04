package com.joe.task.service.knative;

import com.joe.task.config.KubernetesClientManager;
import com.joe.task.dto.knative.KnativeServiceInfo;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KnativeService {

    @Autowired
    private KubernetesClientManager clientManager;

    private static final CustomResourceDefinitionContext KSVC_CONTEXT = new CustomResourceDefinitionContext.Builder()
            .withGroup("serving.knative.dev")
            .withVersion("v1")
            .withPlural("services")
            .withScope("Namespaced")
            .build();

    private final Map<String, KubernetesClient> clientCache = new ConcurrentHashMap<>();

    private synchronized KubernetesClient getOrCreateClient(String env) {
        KubernetesClient client = clientCache.get(env);
        if (client == null || client.getHttpClient().isClosed()) {
            log.info("Creating new Kubernetes client for environment: {}", env);
            client = clientManager.getClient(env);
            clientCache.put(env, client);
        }
        return client;
    }

    private synchronized void closeClient(String env) {
        KubernetesClient client = clientCache.remove(env);
        if (client != null && !client.getHttpClient().isClosed()) {
            try {
                client.close();
                log.info("Closed Kubernetes client for environment: {}", env);
            } catch (Exception e) {
                log.warn("Error closing Kubernetes client for environment: {}", env, e);
            }
        }
    }

    public List<KnativeServiceInfo> getKnativeServicesInNamespace(String env, String namespace) {
        KubernetesClient client = getOrCreateClient(env);
        KubernetesResourceList<Service> serviceList = client.resources(Service.class)
                .inNamespace(namespace)
                .list();

        return serviceList.getItems().stream()
                .map(this::convertToServiceInfo)
                .collect(Collectors.toList());
    }

    public String getKnativeServiceYaml(String env, String namespace, String name) {
        KubernetesClient client = getOrCreateClient(env);
        Service service = client.resources(Service.class)
                .inNamespace(namespace)
                .withName(name)
                .get();
        
        if (service == null) {
            throw new RuntimeException("Knative service not found");
        }
        
        return service.toString();
    }

    public void deleteKnativeService(String env, String namespace, String name) {
        KubernetesClient client = getOrCreateClient(env);
        client.resources(Service.class)
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    private KnativeServiceInfo convertToServiceInfo(Service service) {
        KnativeServiceInfo info = new KnativeServiceInfo();
        info.setName(service.getMetadata().getName());
        info.setNamespace(service.getMetadata().getNamespace());
        
        if (service.getStatus() != null) {
            info.setUrl(service.getStatus().getUrl());
            info.setGeneration(String.valueOf(service.getStatus().getObservedGeneration()));
            info.setLatestCreatedRevision(service.getStatus().getLatestCreatedRevisionName());
            info.setLatestReadyRevision(service.getStatus().getLatestReadyRevisionName());
            info.setObservedGeneration(String.valueOf(service.getStatus().getObservedGeneration()));
            
            if (service.getStatus().getConditions() != null) {
                List<KnativeServiceInfo.KnativeCondition> conditions = new ArrayList<>();
                service.getStatus().getConditions().forEach(c -> {
                    KnativeServiceInfo.KnativeCondition condition = new KnativeServiceInfo.KnativeCondition();
                    condition.setType(c.getType());
                    condition.setStatus(c.getStatus());
                    condition.setReason(c.getReason());
                    condition.setMessage(c.getMessage());
                    condition.setLastTransitionTime(c.getLastTransitionTime());
                    conditions.add(condition);
                });
                info.setConditions(conditions);
                
                // Set overall status based on Ready condition
                service.getStatus().getConditions().stream()
                        .filter(c -> "Ready".equals(c.getType()))
                        .findFirst()
                        .ifPresent(c -> info.setStatus(c.getStatus()));
            }
        }
        
        return info;
    }

    public Map<String, Object> listServices(String env, String namespace, String name, String status) {
        KubernetesClient client = getOrCreateClient(env);
        try {
            // Get Knative services
            GenericKubernetesResourceList serviceList;
            if (namespace != null && !namespace.isEmpty()) {
                serviceList = client.genericKubernetesResources(KSVC_CONTEXT)
                        .inNamespace(namespace)
                        .list();
            } else {
                serviceList = client.genericKubernetesResources(KSVC_CONTEXT)
                        .inAnyNamespace()
                        .list();
            }

            // Apply filters
            List<GenericKubernetesResource> filteredItems = serviceList.getItems().stream()
                    .filter(item -> {
                        String serviceName = item.getMetadata().getName();
                        Map<String, Object> serviceStatus = (Map<String, Object>) item.getAdditionalProperties().get("status");

                        boolean nameMatch = true;
                        if (name != null && !name.isEmpty()) {
                            nameMatch = serviceName.toLowerCase().contains(name.toLowerCase());
                        }

                        boolean statusMatch = true;
                        if (serviceStatus != null && status != null && !status.isEmpty()) {
                            List<Map<String, Object>> conditions = (List<Map<String, Object>>) serviceStatus.get("conditions");
                            if (conditions != null && !conditions.isEmpty()) {
                                Map<String, Object> readyCondition = conditions.stream()
                                        .filter(c -> "Ready".equals(c.get("type")))
                                        .findFirst()
                                        .orElse(null);

                                if (readyCondition != null) {
                                    String currentStatus = (String) readyCondition.get("status");
                                    statusMatch = status.equalsIgnoreCase(currentStatus);
                                }
                            }
                        }

                        return nameMatch && statusMatch;
                    })
                    .toList();

            // Convert to frontend format
            List<Map<String, Object>> resultList = filteredItems.stream()
                    .map(item -> {
                        Map<String, Object> dto = convertToServiceDto(item);
                        dto.put("env", env);  // Add environment information
                        return dto;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("data", resultList);
            return response;
        } catch (Exception e) {
            log.error("Error listing Knative services for environment: {}", env, e);
            // If client is closed, remove it from cache
            if (e.getMessage() != null && e.getMessage().contains("client executor has been shutdown")) {
                closeClient(env);
            }
            throw e;
        }
    }

    private Map<String, Object> convertToServiceDto(GenericKubernetesResource item) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("name", item.getMetadata().getName());
        dto.put("namespace", item.getMetadata().getNamespace());
        dto.put("generation", item.getMetadata().getGeneration());
        dto.put("creationTimestamp", item.getMetadata().getCreationTimestamp());

        // Get status
        Map<String, Object> status = (Map<String, Object>) item.getAdditionalProperties().get("status");
        if (status != null) {
            List<Map<String, Object>> conditions = (List<Map<String, Object>>) status.get("conditions");
            if (conditions != null) {
                Map<String, Object> readyCondition = conditions.stream()
                        .filter(c -> "Ready".equals(c.get("type")))
                        .findFirst()
                        .orElse(null);

                if (readyCondition != null) {
                    dto.put("status", readyCondition.get("status"));
                    dto.put("reason", readyCondition.get("reason"));
                }
            }

            // Get URL
            String url = (String) status.get("url");
            if (url != null) {
                dto.put("url", url);
            }

            // Get latest revisions
            dto.put("latestCreatedRevision", status.get("latestCreatedRevisionName"));
            dto.put("latestReadyRevision", status.get("latestReadyRevisionName"));
            dto.put("observedGeneration", status.get("observedGeneration"));
        }

        return dto;
    }

    public String getServiceYaml(String env, String namespace, String name) {
        KubernetesClient client = getOrCreateClient(env);
        try {
            GenericKubernetesResource service = client.genericKubernetesResources(KSVC_CONTEXT)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            return service != null ? service.toString() : "";
        } catch (Exception e) {
            log.error("Error getting YAML for service {}/{} in environment: {}", namespace, name, env, e);
            if (e.getMessage() != null && e.getMessage().contains("client executor has been shutdown")) {
                closeClient(env);
            }
            throw e;
        }
    }

    public void deleteService(String env, String namespace, String name) {
        KubernetesClient client = getOrCreateClient(env);
        try {
            client.genericKubernetesResources(KSVC_CONTEXT)
                    .inNamespace(namespace)
                    .withName(name)
                    .delete();
        } catch (Exception e) {
            log.error("Error deleting service {}/{} in environment: {}", namespace, name, env, e);
            if (e.getMessage() != null && e.getMessage().contains("client executor has been shutdown")) {
                closeClient(env);
            }
            throw e;
        }
    }

    /**
     * Create a new revision for an existing Knative service by updating its annotation
     * Wait for the revision to be created and become ready
     *
     * @param env The environment (cluster) where the service is deployed
     * @param namespace The namespace where the service is deployed
     * @param name The name of the service
     * @return The name of the newly created revision if it becomes ready, null otherwise
     */
    public String createNewRevision(String env, String namespace, String name) {
        KubernetesClient client = getOrCreateClient(env);
        try {
            log.info("Starting to create new revision for service {}/{} in environment: {}", namespace, name, env);
            
            // Get the current service
            GenericKubernetesResource service = client.genericKubernetesResources(KSVC_CONTEXT)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (service == null) {
                throw new RuntimeException("Service " + name + " not found in namespace " + namespace);
            }

            // Get current latest revision name for comparison
            Map<String, Object> currentStatus = (Map<String, Object>) service.getAdditionalProperties().get("status");
            String currentRevision = currentStatus != null ? (String) currentStatus.get("latestCreatedRevisionName") : null;
            log.info("Current revision for service {}/{}: {}", namespace, name, currentRevision);

            // Get the spec section
            Map<String, Object> spec = (Map<String, Object>) service.getAdditionalProperties().get("spec");
            if (spec == null) {
                throw new RuntimeException("Service spec is null");
            }

            // Get the template section
            Map<String, Object> template = (Map<String, Object>) spec.get("template");
            if (template == null) {
                template = new HashMap<>();
                spec.put("template", template);
            }

            // Get or create metadata in template
            Map<String, Object> templateMetadata = (Map<String, Object>) template.get("metadata");
            if (templateMetadata == null) {
                templateMetadata = new HashMap<>();
                template.put("metadata", templateMetadata);
            }

            // Get or create annotations in template metadata
            Map<String, String> templateAnnotations = (Map<String, String>) templateMetadata.get("annotations");
            if (templateAnnotations == null) {
                templateAnnotations = new HashMap<>();
                templateMetadata.put("annotations", templateAnnotations);
            }

            // Update the timestamp annotation in the template
            String timestamp = String.valueOf(System.currentTimeMillis());
            templateAnnotations.put("app.kubernetes.io/revision-timestamp", timestamp);
            log.info("Updating service {}/{} template with revision timestamp: {}", namespace, name, timestamp);

            // Update the service
            service = client.genericKubernetesResources(KSVC_CONTEXT)
                    .inNamespace(namespace)
                    .withName(name)
                    .patch(service);
            
            log.info("Service {}/{} updated successfully, starting to poll for new revision", namespace, name);

            // Poll for the new revision and check its status
            long startTime = System.currentTimeMillis();
            long timeout = 60000; // 60 seconds timeout
            long pollInterval = 2000; // 2 seconds interval
            String newRevision = null;
            int pollCount = 0;

            while (System.currentTimeMillis() - startTime < timeout) {
                pollCount++;
                log.debug("Poll attempt #{} for service {}/{}", pollCount, namespace, name);
                
                GenericKubernetesResource updatedService = client.genericKubernetesResources(KSVC_CONTEXT)
                        .inNamespace(namespace)
                        .withName(name)
                        .get();

                Map<String, Object> status = (Map<String, Object>) updatedService.getAdditionalProperties().get("status");
                if (status != null) {
                    String latestRevision = (String) status.get("latestCreatedRevisionName");
                    log.debug("Current latest revision: {}, previous revision: {}", latestRevision, currentRevision);
                    
                    // Check if we have a new revision
                    if (latestRevision != null && !latestRevision.equals(currentRevision)) {
                        newRevision = latestRevision;
                        log.info("New revision detected: {} for service {}/{} (poll #{})", 
                               latestRevision, namespace, name, pollCount);
                        
                        // Check if the revision is ready
                        List<Map<String, Object>> conditions = (List<Map<String, Object>>) status.get("conditions");
                        if (conditions != null) {
                            log.debug("Checking conditions for revision {}: {}", latestRevision, conditions);
                            boolean isReady = conditions.stream()
                                    .anyMatch(c -> "Ready".equals(c.get("type")) && 
                                                 "True".equals(c.get("status")));
                            
                            if (isReady) {
                                log.info("New revision {} is ready for service {}/{} (after {} polls)", 
                                       latestRevision, namespace, name, pollCount);
                                return latestRevision;
                            } else {
                                log.debug("Revision {} exists but not ready yet", latestRevision);
                            }
                        }
                    } else {
                        log.debug("No new revision detected yet (poll #{})", pollCount);
                    }
                } else {
                    log.warn("Status is null for service {}/{} (poll #{})", namespace, name, pollCount);
                }

                try {
                    Thread.sleep(pollInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Operation was interrupted", e);
                }
            }

            // If we got here, we either:
            // 1. Never got a new revision
            // 2. Got a new revision but it never became ready
            if (newRevision == null) {
                log.error("Timeout after {} polls - no new revision was created for service {}/{}", 
                         pollCount, namespace, name);
                throw new RuntimeException("Timeout waiting for new revision to be created");
            } else {
                log.error("Timeout after {} polls - revision {} was created but never became ready for service {}/{}", 
                         pollCount, newRevision, namespace, name);
                throw new RuntimeException("Timeout waiting for revision " + newRevision + " to become ready");
            }

        } catch (Exception e) {
            log.error("Error creating new revision for service {}/{} in environment: {}", namespace, name, env, e);
            if (e.getMessage() != null && e.getMessage().contains("client executor has been shutdown")) {
                closeClient(env);
            }
            throw e;
        }
    }

    /**
     * Update a Knative service by creating a new revision
     * @param env The environment (cluster) where the service is deployed
     * @param namespace The namespace where the service is deployed
     * @param name The name of the service
     * @return Response map containing status code and message
     */
    public Map<String, Object> updateService(String env, String namespace, String name) {
        Map<String, Object> response = new HashMap<>();
        try {
            String newRevision = createNewRevision(env, namespace, name);
            response.put("code", 0);
            response.put("msg", newRevision);
            return response;
        } catch (Exception e) {
            log.error("Error updating service {}/{} in environment: {}", namespace, name, env, e);
            response.put("code", 1);
            response.put("msg", e.getMessage());
            return response;
        }
    }
}

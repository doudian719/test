package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import com.joe.task.dto.ConfigMapInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConfigMapService extends BaseService {
    private final KubernetesClientManager clientManager;

    public ConfigMapService(KubernetesClientManager clientManager) {
        super(clientManager);
        this.clientManager = clientManager;
    }

    public List<ConfigMapInfo> getConfigMapsInNamespace(String env, 
                                                      String namespace, 
                                                      Optional<String> name) {
        return clientManager.getClient(env)
                .configMaps()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .filter(configMap -> !name.isPresent() || 
                        StringUtils.containsIgnoreCase(configMap.getMetadata().getName(), name.get()))
                .map(configMap -> ConfigMapInfo.builder()
                        .name(configMap.getMetadata().getName())
                        .namespace(configMap.getMetadata().getNamespace())
                        .creationTimestamp(configMap.getMetadata().getCreationTimestamp())
                        .data(configMap.getData())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Update ConfigMap data
     * @param env Environment name
     * @param namespace Namespace name
     * @param name ConfigMap name
     * @param data New ConfigMap data
     * @return true if update successful
     */
    public boolean updateConfigMap(String env, String namespace, String name, Map<String, String> data) {
        try {
            var client = clientManager.getClient(env);
            var configMap = client.configMaps()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (configMap == null) {
                log.error("ConfigMap not found: {} in namespace: {}", name, namespace);
                return false;
            }

            // Update the data
            configMap.setData(data);

            // Apply the update
            client.configMaps()
                    .inNamespace(namespace)
                    .withName(name)
                    .replace(configMap);

            log.info("Successfully updated ConfigMap: {} in namespace: {}", name, namespace);
            return true;
        } catch (Exception e) {
            log.error("Error updating ConfigMap: {} in namespace: {}", name, namespace, e);
            return false;
        }
    }
} 
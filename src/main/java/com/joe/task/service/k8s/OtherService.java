package com.joe.task.service.k8s;


import com.joe.task.config.KubernetesClientManager;
import com.joe.task.dto.ConfigMapInfo;
import com.joe.task.dto.SecretInfo;
import com.joe.task.dto.ServiceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OtherService extends BaseService
{
    private final KubernetesClientManager clientManager;

    public OtherService(KubernetesClientManager clientManager)
    {
        super(clientManager);
        this.clientManager = clientManager;
    }

    // Service operations
    public List<ServiceInfo> getServicesInNamespace(String env, String namespace) {
        return clientManager.getClient(env).services()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .map(service -> new ServiceInfo(
                        service.getMetadata().getName(),
                        service.getSpec().getType(),
                        service.getSpec().getClusterIP(),
                        service.getSpec().getPorts()))
                .collect(Collectors.toList());
    }

    // ConfigMap operations
    public List<ConfigMapInfo> getConfigMapsInNamespace(String env, String namespace) {
        return clientManager.getClient(env).configMaps()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .map(configMap -> ConfigMapInfo.builder()
                        .name(configMap.getMetadata().getName())
                        .namespace(configMap.getMetadata().getNamespace())
                        .creationTimestamp(configMap.getMetadata().getCreationTimestamp())
                        .data(configMap.getData())
                        .build())
                .collect(Collectors.toList());
    }

    // Secret operations (only returning metadata, not the actual secret values)
    public List<SecretInfo> getSecretsInNamespace(String env, String namespace) {
        return clientManager.getClient(env).secrets()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .map(secret -> SecretInfo.builder()
                        .name(secret.getMetadata().getName())
                        .namespace(secret.getMetadata().getNamespace())
                        .creationTimestamp(secret.getMetadata().getCreationTimestamp())
                        .type(secret.getType())
                        .data(secret.getData() != null ? 
                             secret.getData().keySet().stream()
                                 .collect(Collectors.toMap(key -> key, key -> "******")) : 
                             null)
                        .build())
                .collect(Collectors.toList());
    }

}

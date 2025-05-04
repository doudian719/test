package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import com.joe.task.dto.SecretInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SecretService extends BaseService {
    private final KubernetesClientManager clientManager;

    public SecretService(KubernetesClientManager clientManager) {
        super(clientManager);
        this.clientManager = clientManager;
    }

    public List<SecretInfo> getSecretsInNamespace(String env,
                                                String namespace,
                                                Optional<String> name) {
        return clientManager.getClient(env)
                .secrets()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .filter(secret -> !name.isPresent() || 
                        StringUtils.containsIgnoreCase(secret.getMetadata().getName(), name.get()))
                .map(secret -> {
                    // 获取secret的完整数据
                    Map<String, String> secretData = secret.getData() != null ?
                            secret.getData().entrySet().stream()
                                    .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> new String(java.util.Base64.getDecoder().decode(e.getValue()))
                                    )) :
                            null;
                            
                    return SecretInfo.builder()
                            .name(secret.getMetadata().getName())
                            .namespace(secret.getMetadata().getNamespace())
                            .creationTimestamp(secret.getMetadata().getCreationTimestamp())
                            .type(secret.getType())
                            .data(secretData)
                            .build();
                })
                .collect(Collectors.toList());
    }
} 
package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import com.joe.task.dto.DeploymentInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
@Slf4j
public class DeploymentService extends BaseService
{
    private final KubernetesClientManager clientManager;

    public DeploymentService(KubernetesClientManager clientManager)
    {
        super(clientManager);
        this.clientManager = clientManager;
    }

    public List<DeploymentInfo> getDeploymentsInNamespace(String env, String namespace, Optional<String> name) {
        return clientManager.getClient(env).apps()
                .deployments()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .map(deployment -> {
                    if(name.isPresent() && !StringUtils.containsIgnoreCase(deployment.getMetadata().getName(), name.get()))  {
                        return null;
                    }

                    String revision = deployment.getMetadata().getAnnotations()
                            .get("deployment.kubernetes.io/revision");
                    String observedGeneration = String.valueOf(deployment.getStatus().getObservedGeneration());
                    Integer updatedReplicas = deployment.getStatus().getUpdatedReplicas();
                    Integer desiredReplicas = deployment.getSpec().getReplicas();

                    return DeploymentInfo.builder()
                            .name(deployment.getMetadata().getName())
                            .namespace(deployment.getMetadata().getNamespace())
                            .desiredReplicas(deployment.getSpec().getReplicas())
                            .readyReplicas(deployment.getStatus().getReadyReplicas())
                            .updatedReplicas(updatedReplicas)
                            .creationTimestamp(deployment.getMetadata().getCreationTimestamp())
                            .revision(revision)
                            .observedGeneration(observedGeneration)
                            .isInProgress(updatedReplicas == null || updatedReplicas < desiredReplicas)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}

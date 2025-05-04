package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import com.joe.task.dto.DeploymentInfo;
import com.joe.task.dto.PodInfo;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class CommonHealthService extends BaseService
{
    private final KubernetesClientManager clientManager;

    @Autowired
    DeploymentService deploymentService;

    @Autowired
    PodService podService;


    public CommonHealthService(KubernetesClientManager clientManager)
    {
        super(clientManager);
        this.clientManager = clientManager;
    }

    public void checkPodsHealth(String env)
    {
        KubernetesClient client = clientManager.getClient(env);
        List<Namespace> namespaces = client.namespaces().list().getItems();

        for (Namespace ns : namespaces)
        {
            String namespaceName = ns.getMetadata().getName();
            checkNamespacePods(env, namespaceName);
        }
    }

    private void checkNamespacePods(String env, String namespace)
    {
        // 检查 Pod 状态
        List<PodInfo> pods = podService.getPodsInNamespace(env, namespace, Optional.empty(), Optional.empty());
        for (PodInfo pod : pods)
        {
            checkPodHealth(pod);
        }

        // 检查 Deployment 状态
        List<DeploymentInfo> deployments = deploymentService.getDeploymentsInNamespace(env, namespace, Optional.empty());
        for (DeploymentInfo deployment : deployments)
        {
            checkDeploymentHealth(deployment);
        }
    }

    private void checkPodHealth(PodInfo pod) {
        // 1. Check for CrashLoopBackOff
        if ("CrashLoopBackOff".equals(pod.getStatus())) {
            log.error("Pod {} in namespace {} is in CrashLoopBackOff state",
                    pod.getName(), pod.getNamespace());
            // TODO: Send notification
        }

        // 2. Check for high restart count (e.g., > 5)
        if (pod.getRestartCount() > 5) {
            log.error("Pod {} in namespace {} has high restart count: {}",
                    pod.getName(), pod.getNamespace(), pod.getRestartCount());
            // TODO: Send notification
        }

        // 3. Check for pods stuck in Pending state for too long (e.g., > 10 minutes)
        if ("Pending".equals(pod.getStatus()) && pod.getStartTimeMillis() != null) {
            long pendingDuration = System.currentTimeMillis() - pod.getStartTimeMillis();
            if (pendingDuration > 10 * 60 * 1000) { // 10 minutes
                log.error("Pod {} in namespace {} stuck in Pending state for too long",
                        pod.getName(), pod.getNamespace());
                // TODO: Send notification
            }
        }

        // 4. Check for ImagePullBackOff
        if ("ImagePullBackOff".equals(pod.getStatus())) {
            log.error("Pod {} in namespace {} failed to pull image",
                    pod.getName(), pod.getNamespace());
            // TODO: Send notification
        }

        // 5. Check for Failed state
        if ("Failed".equals(pod.getStatus())) {
            log.error("Pod {} in namespace {} is in Failed state",
                    pod.getName(), pod.getNamespace());
            // TODO: Send notification
        }
    }

    private void checkDeploymentHealth(DeploymentInfo deployment) {
        // Skip if this is not the latest deployment
        if (!isLatestDeployment(deployment)) {
            log.debug("Skipping health check for old deployment: {}", deployment.getName());
            return;
        }

        // Skip if deployment is still in progress
        if (deployment.isInProgress()) {
            log.debug("Deployment {} is still in progress, skipping health check", deployment.getName());
            return;
        }

        // Check for insufficient replicas
        if (deployment.getDesiredReplicas() > 0 &&
                (deployment.getReadyReplicas() == null || deployment.getReadyReplicas() == 0)) {
            log.error("Deployment {} in namespace {} has 0 ready replicas (desired: {})",
                    deployment.getName(),
                    deployment.getNamespace(),
                    deployment.getDesiredReplicas());
            // TODO: Send notification
        }

        if (deployment.getReadyReplicas() != null &&
                deployment.getReadyReplicas() < deployment.getDesiredReplicas()) {
            log.error("Deployment {} in namespace {} has insufficient replicas (ready: {}, desired: {})",
                    deployment.getName(),
                    deployment.getNamespace(),
                    deployment.getReadyReplicas(),
                    deployment.getDesiredReplicas());
            // TODO: Send notification
        }
    }

    private boolean isLatestDeployment(DeploymentInfo deployment) {
        return deployment.getRevision() != null &&
                deployment.getObservedGeneration() != null &&
                deployment.getRevision().equals(deployment.getObservedGeneration());
    }

}

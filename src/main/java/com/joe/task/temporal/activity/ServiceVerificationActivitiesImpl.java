package com.joe.task.temporal.activity;

import com.joe.task.config.KubernetesClientManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.knative.serving.v1.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;
import io.temporal.failure.ApplicationFailure;

@Slf4j
@Component
public class ServiceVerificationActivitiesImpl implements ServiceVerificationActivities {
    
    private final KubernetesClientManager clientManager;
    private final RestTemplate restTemplate;
    
    @Autowired
    public ServiceVerificationActivitiesImpl(KubernetesClientManager clientManager, RestTemplate restTemplate) {
        this.clientManager = clientManager;
        this.restTemplate = restTemplate;
    }
    
    @Override
    public boolean checkKSVCInK8S(String env, String namespace, String serviceName) {
        log.info("Checking KSVC in K8S for service: {} in namespace: {} env: {}", serviceName, namespace, env);
        
        // Get K8s client for the specified environment
        KubernetesClient client = clientManager.getClient(env);
        
        // Get Knative client from K8s client
        KnativeClient knativeClient = client.adapt(KnativeClient.class);
        
        // Get the Knative service
        Service service = knativeClient.services()
            .inNamespace(namespace)
            .withName(serviceName)
            .get();
            
        if (service == null) {
            String errorMsg = String.format("Service %s not found in K8S namespace: %s env: %s", 
                serviceName, namespace, env);
            log.error(errorMsg);
            throw ApplicationFailure.newFailure(errorMsg, "SERVICE_NOT_FOUND");
        }
        
        // Check if service is ready
        boolean isReady = service.getStatus().getConditions().stream()
            .anyMatch(condition -> 
                "Ready".equals(condition.getType()) && 
                "True".equals(condition.getStatus()));
                
        if (!isReady) {
            String errorMsg = String.format("Service %s in namespace %s env %s is not ready", 
                serviceName, namespace, env);
            log.error(errorMsg);
            throw ApplicationFailure.newFailure(errorMsg, "SERVICE_NOT_READY");
        }
        
        log.info("Service {} ready status in namespace {} env {}: {}", serviceName, namespace, env, isReady);
        return true;
    }
    
    @Override
    public boolean checkWithHasura(String env, String namespace, String serviceName) {
        log.info("Checking with Hasura for service: {} in namespace: {} env: {}", serviceName, namespace, env);
        
        try {
            // Hasura GraphQL endpoint (should be configured in properties)
            String hasuraEndpoint = String.format("http://hasura-%s:8080/v1/graphql", env);
            
            // GraphQL query to check service status
            String query = String.format("""
                {
                  services(where: {name: {_eq: "%s"}, namespace: {_eq: "%s"}}) {
                    status
                    is_active
                  }
                }
                """, serviceName, namespace);
                
            // TODO: Execute GraphQL query using RestTemplate
            // This is a simplified check - implement actual Hasura query logic
            boolean hasuraStatus = true;
            
            if (!hasuraStatus) {
                String errorMsg = String.format("Service %s in namespace %s env %s is not active in Hasura", 
                    serviceName, namespace, env);
                log.error(errorMsg);
                throw ApplicationFailure.newFailure(errorMsg, "SERVICE_NOT_ACTIVE_IN_HASURA");
            }
            
            log.info("Hasura check result for service {} in namespace {} env {}: {}", 
                serviceName, namespace, env, hasuraStatus);
            return true;
            
        } catch (Exception e) {
            String errorMsg = String.format("Error checking Hasura for service %s in namespace %s: %s", 
                serviceName, namespace, e.getMessage());
            log.error(errorMsg, e);
            throw ApplicationFailure.newFailure(errorMsg, "HASURA_CHECK_FAILED", e);
        }
    }
    
    @Override
    public boolean verifyService(String namespace, String serviceName) {
        log.info("Verifying service: {} in namespace: {}", serviceName, namespace);
        
        try {
            // Get service URL (should be constructed based on your environment)
            String serviceUrl = String.format("http://%s.%s.svc.cluster.local", serviceName, namespace);
            
            // Try to call service health endpoint
            String healthEndpoint = serviceUrl + "/health";
            String response = restTemplate.getForObject(healthEndpoint, String.class);
            
            boolean isHealthy = response != null && response.contains("UP");
            
            if (!isHealthy) {
                String errorMsg = String.format("Service %s in namespace %s health check failed. Response: %s", 
                    serviceName, namespace, response);
                log.error(errorMsg);
                throw ApplicationFailure.newFailure(errorMsg, "SERVICE_HEALTH_CHECK_FAILED");
            }
            
            log.info("Service {} health check result in namespace {}: {}", serviceName, namespace, isHealthy);
            return true;
            
        } catch (Exception e) {
            String errorMsg = String.format("Error verifying service %s in namespace %s: %s", 
                serviceName, namespace, e.getMessage());
            log.error(errorMsg, e);
            throw ApplicationFailure.newFailure(errorMsg, "SERVICE_VERIFICATION_FAILED", e);
        }
    }
} 
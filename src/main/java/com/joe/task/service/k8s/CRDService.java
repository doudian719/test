package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CRDService extends BaseService {
    private final KubernetesClientManager clientManager;

    public CRDService(KubernetesClientManager clientManager) {
        super(clientManager);
        this.clientManager = clientManager;
    }

    /**
     * Get all CRDs in the cluster
     * @param env Environment name
     * @return List of CRDs
     */
    public List<CustomResourceDefinition> getCRDs(String env) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Fetching CRDs for environment: {}", env);
        try {
            CustomResourceDefinitionList crdList = client.apiextensions().v1().customResourceDefinitions().list();
            return crdList.getItems();
        } catch (Exception e) {
            log.error("Error fetching CRDs for environment: {}", env, e);
            throw new RuntimeException("Failed to fetch CRDs", e);
        }
    }

    /**
     * Get a specific CRD by name
     * @param env Environment name
     * @param crdName CRD name
     * @return CRD details
     */
    public CustomResourceDefinition getCRD(String env, String crdName) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Fetching CRD: {} for environment: {}", crdName, env);
        try {
            return client.apiextensions().v1().customResourceDefinitions().withName(crdName).get();
        } catch (Exception e) {
            log.error("Error fetching CRD: {} for environment: {}", crdName, env, e);
            throw new RuntimeException("Failed to fetch CRD", e);
        }
    }

    /**
     * Create a new CRD
     * @param env Environment name
     * @param crd CRD to create
     * @return Created CRD
     */
    public CustomResourceDefinition createCRD(String env, CustomResourceDefinition crd) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Creating CRD: {} for environment: {}", crd.getMetadata().getName(), env);
        try {
            return client.apiextensions().v1().customResourceDefinitions().create(crd);
        } catch (Exception e) {
            log.error("Error creating CRD: {} for environment: {}", crd.getMetadata().getName(), env, e);
            throw new RuntimeException("Failed to create CRD", e);
        }
    }

    /**
     * Update an existing CRD
     * @param env Environment name
     * @param crd CRD to update
     * @return Updated CRD
     */
    public CustomResourceDefinition updateCRD(String env, CustomResourceDefinition crd) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Updating CRD: {} for environment: {}", crd.getMetadata().getName(), env);
        try {
            return client.apiextensions().v1().customResourceDefinitions().withName(crd.getMetadata().getName()).replace(crd);
        } catch (Exception e) {
            log.error("Error updating CRD: {} for environment: {}", crd.getMetadata().getName(), env, e);
            throw new RuntimeException("Failed to update CRD", e);
        }
    }

    /**
     * Delete a CRD
     * @param env Environment name
     * @param crdName CRD name to delete
     */
    public void deleteCRD(String env, String crdName) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Deleting CRD: {} for environment: {}", crdName, env);
        try {
            client.apiextensions().v1().customResourceDefinitions().withName(crdName).delete();
        } catch (Exception e) {
            log.error("Error deleting CRD: {} for environment: {}", crdName, env, e);
            throw new RuntimeException("Failed to delete CRD", e);
        }
    }

    /**
     * Get all instances of a specific CRD
     * @param env Environment name
     * @param crdName CRD name
     * @param namespace Namespace (optional)
     * @return List of CRD instances
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCRDInstances(String env, String crdName, String namespace) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Fetching instances of CRD: {} for environment: {} in namespace: {}", crdName, env, namespace);
        try {
            CustomResourceDefinition crd = getCRD(env, crdName);
            String group = crd.getSpec().getGroup();
            String version = crd.getSpec().getVersions().get(0).getName();
            String plural = crd.getSpec().getNames().getPlural();

            CustomResourceDefinitionContext context = new CustomResourceDefinitionContext.Builder()
                    .withGroup(group)
                    .withVersion(version)
                    .withPlural(plural)
                    .build();

            if (namespace != null && !namespace.isEmpty()) {
                return client.genericKubernetesResources(context).list().getItems().stream()
                        .filter(item -> namespace.equals(item.getMetadata().getNamespace()))
                        .map(item -> (Map<String, Object>) client.getKubernetesSerialization().convertValue(item, Map.class))
                        .collect(java.util.stream.Collectors.toList());
            } else {
                return client.genericKubernetesResources(context).list().getItems().stream()
                        .map(item -> (Map<String, Object>) client.getKubernetesSerialization().convertValue(item, Map.class))
                        .collect(java.util.stream.Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Error fetching instances of CRD: {} for environment: {}", crdName, env, e);
            throw new RuntimeException("Failed to fetch CRD instances", e);
        }
    }

    /**
     * Get a specific CRD instance
     * @param env Environment name
     * @param crdName CRD name
     * @param namespace Namespace
     * @param instanceName Instance name
     * @return CRD instance details
     */
    public Map<String, Object> getCRDInstance(String env, String crdName, String namespace, String instanceName) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Fetching instance: {} of CRD: {} for environment: {} in namespace: {}", 
                instanceName, crdName, env, namespace);
        try {
            CustomResourceDefinition crd = getCRD(env, crdName);
            String group = crd.getSpec().getGroup();
            String version = crd.getSpec().getVersions().get(0).getName();
            String plural = crd.getSpec().getNames().getPlural();
            String scope = crd.getSpec().getScope();

            CustomResourceDefinitionContext context = new CustomResourceDefinitionContext.Builder()
                    .withGroup(group)
                    .withVersion(version)
                    .withPlural(plural)
                    .withScope(scope)
                    .build();

            Object instance;
            if ("Namespaced".equalsIgnoreCase(scope)) {
                if (namespace == null || namespace.trim().isEmpty()) {
                    throw new RuntimeException("namespace不能为空，请选择命名空间后再操作！");
                }
                instance = client.genericKubernetesResources(context).inNamespace(namespace).withName(instanceName).get();
            } else {
                instance = client.genericKubernetesResources(context).withName(instanceName).get();
            }
            return client.getKubernetesSerialization().convertValue(instance, Map.class);
        } catch (Exception e) {
            log.error("Error fetching instance: {} of CRD: {} for environment: {}", 
                    instanceName, crdName, env, e);
            throw new RuntimeException("Failed to fetch CRD instance", e);
        }
    }

    // 过滤只读字段，防止创建时K8s报错
    @SuppressWarnings("unchecked")
    private Map<String, Object> filterForCreate(Map<String, Object> instance) {
        instance.remove("status");
        if (instance.containsKey("metadata")) {
            Map<String, Object> metadata = (Map<String, Object>) instance.get("metadata");
            metadata.remove("resourceVersion");
            metadata.remove("uid");
            metadata.remove("creationTimestamp");
            metadata.remove("managedFields");
            metadata.remove("generation");
        }
        return instance;
    }

    /**
     * Create a new CRD instance
     * @param env Environment name
     * @param crdName CRD name
     * @param namespace Namespace
     * @param instance Instance data
     * @return Created instance
     */
    public Map<String, Object> createCRDInstance(String env, String crdName, String namespace, Map<String, Object> instance) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Creating instance of CRD: {} for environment: {} in namespace: {}", crdName, env, namespace);
        try {
            CustomResourceDefinition crd = getCRD(env, crdName);
            String group = crd.getSpec().getGroup();
            String version = crd.getSpec().getVersions().get(0).getName();
            String plural = crd.getSpec().getNames().getPlural();
            String scope = crd.getSpec().getScope();

            CustomResourceDefinitionContext context = new CustomResourceDefinitionContext.Builder()
                    .withGroup(group)
                    .withVersion(version)
                    .withPlural(plural)
                    .withScope(scope)
                    .build();

            // 创建前过滤只读字段
            instance = filterForCreate(instance);

            ObjectMapper mapper = new ObjectMapper();
            GenericKubernetesResource resource = mapper.convertValue(instance, GenericKubernetesResource.class);
            Object createdInstance;
            if ("Namespaced".equalsIgnoreCase(scope)) {
                if (namespace == null || namespace.trim().isEmpty()) {
                    throw new RuntimeException("namespace不能为空，请选择命名空间后再创建！");
                }
                resource.getMetadata().setNamespace(namespace);
                createdInstance = client.genericKubernetesResources(context).inNamespace(namespace).create(resource);
            } else {
                createdInstance = client.genericKubernetesResources(context).create(resource);
            }
            return client.getKubernetesSerialization().convertValue(createdInstance, Map.class);
        } catch (Exception e) {
            log.error("Error creating instance of CRD: {} for environment: {}", crdName, env, e);
            throw new RuntimeException("Failed to create CRD instance", e);
        }
    }

    /**
     * Update a CRD instance
     * @param env Environment name
     * @param crdName CRD name
     * @param namespace Namespace
     * @param instanceName Instance name
     * @param instance Updated instance data
     * @return Updated instance
     */
    public Map<String, Object> updateCRDInstance(String env, String crdName, String namespace, 
            String instanceName, Map<String, Object> instance) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Updating instance: {} of CRD: {} for environment: {} in namespace: {}", 
                instanceName, crdName, env, namespace);
        try {
            CustomResourceDefinition crd = getCRD(env, crdName);
            String group = crd.getSpec().getGroup();
            String version = crd.getSpec().getVersions().get(0).getName();
            String plural = crd.getSpec().getNames().getPlural();
            String scope = crd.getSpec().getScope();

            CustomResourceDefinitionContext context = new CustomResourceDefinitionContext.Builder()
                    .withGroup(group)
                    .withVersion(version)
                    .withPlural(plural)
                    .withScope(scope)
                    .build();

            ObjectMapper mapper = new ObjectMapper();
            GenericKubernetesResource resource = mapper.convertValue(instance, GenericKubernetesResource.class);
            resource.getMetadata().setName(instanceName);
            Object updatedInstance;
            if ("Namespaced".equalsIgnoreCase(scope)) {
                if (namespace == null || namespace.trim().isEmpty()) {
                    throw new RuntimeException("namespace不能为空，请选择命名空间后再保存！");
                }
                resource.getMetadata().setNamespace(namespace);
                updatedInstance = client.genericKubernetesResources(context)
                    .inNamespace(namespace)
                    .withName(instanceName)
                    .replace(resource);
            } else {
                updatedInstance = client.genericKubernetesResources(context)
                    .withName(instanceName)
                    .replace(resource);
            }
            return client.getKubernetesSerialization().convertValue(updatedInstance, Map.class);
        } catch (Exception e) {
            log.error("Error updating instance: {} of CRD: {} for environment: {}", 
                    instanceName, crdName, env, e);
            throw new RuntimeException("Failed to update CRD instance", e);
        }
    }

    /**
     * Delete a CRD instance
     * @param env Environment name
     * @param crdName CRD name
     * @param namespace Namespace
     * @param instanceName Instance name to delete
     */
    public void deleteCRDInstance(String env, String crdName, String namespace, String instanceName) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Deleting instance: {} of CRD: {} for environment: {} in namespace: {}", 
                instanceName, crdName, env, namespace);
        try {
            CustomResourceDefinition crd = getCRD(env, crdName);
            String group = crd.getSpec().getGroup();
            String version = crd.getSpec().getVersions().get(0).getName();
            String plural = crd.getSpec().getNames().getPlural();
            String scope = crd.getSpec().getScope();

            CustomResourceDefinitionContext context = new CustomResourceDefinitionContext.Builder()
                    .withGroup(group)
                    .withVersion(version)
                    .withPlural(plural)
                    .withScope(scope)
                    .build();

            if ("Namespaced".equalsIgnoreCase(scope)) {
                if (namespace == null || namespace.trim().isEmpty()) {
                    throw new RuntimeException("namespace不能为空，请选择命名空间后再删除！");
                }
                client.genericKubernetesResources(context).inNamespace(namespace).withName(instanceName).delete();
            } else {
                client.genericKubernetesResources(context).withName(instanceName).delete();
            }
        } catch (Exception e) {
            log.error("Error deleting instance: {} of CRD: {} for environment: {}", 
                    instanceName, crdName, env, e);
            throw new RuntimeException("Failed to delete CRD instance", e);
        }
    }
} 
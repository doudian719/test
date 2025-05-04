package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.api.model.ObjectMeta;

@Service
@Slf4j
public class CommandService extends BaseService
{
    private final KubernetesClientManager clientManager;

    public CommandService(KubernetesClientManager clientManager)
    {
        super(clientManager);
        this.clientManager = clientManager;
    }

    public String executeCommand(String env, String command)
    {
        KubernetesClient kubernetesClient = clientManager.getClient(env);
        if (kubernetesClient == null) {
            return "Error: Could not get Kubernetes client for environment: " + env;
        }

        try {
            // Remove "kubectl" from the beginning of the command if present
            command = command.trim();
            if (command.startsWith("kubectl ")) {
                command = command.substring("kubectl ".length());
            }

            // Parse the command to get namespace and other parts
            String[] parts = command.split("\\s+");
            String namespace = "default"; // default namespace
            String actualCommand = command;

            // Check if namespace is specified
            for (int i = 0; i < parts.length - 1; i++) {
                if (parts[i].equals("-n") || parts[i].equals("--namespace")) {
                    namespace = parts[i + 1];
                    break;
                }
            }

            // Execute the command using the Kubernetes client API
            if (command.startsWith("get ")) {
                return handleGetCommand(kubernetesClient, namespace, command);
            } else if (command.startsWith("describe ")) {
                return handleDescribeCommand(kubernetesClient, namespace, command);
            } else if (command.startsWith("logs ")) {
                return handleLogsCommand(kubernetesClient, namespace, command);
            } else if (command.startsWith("create ")) {
                return handleCreateCommand(kubernetesClient, namespace, command);
            } else if (command.startsWith("delete ")) {
                return handleDeleteCommand(kubernetesClient, namespace, command);
            } else {
                return "Unsupported command. Currently supports: get, describe, logs, create, delete";
            }
        } catch (Exception e) {
            log.error("Error executing command: {}", command, e);
            return "Error executing command: " + e.getMessage();
        }
    }

    private String handleGetCommand(KubernetesClient client, String namespace, String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            return "Invalid get command";
        }

        String resource = parts[1];
        try {
            switch (resource.toLowerCase()) {
                case "pods":
                case "pod":
                case "po":
                    return client.pods().inNamespace(namespace).list().toString();
                case "services":
                case "service":
                case "svc":
                    return client.services().inNamespace(namespace).list().toString();
                case "deployments":
                case "deployment":
                case "deploy":
                    return client.apps().deployments().inNamespace(namespace).list().toString();
                case "configmaps":
                case "configmap":
                case "cm":
                    return client.configMaps().inNamespace(namespace).list().toString();
                case "secrets":
                case "secret":
                    return client.secrets().inNamespace(namespace).list().toString();
                case "namespaces":
                case "namespace":
                case "ns":
                    return client.namespaces().list().toString();
                case "ksvc":
                case "knativeservices":
                case "knativeservice":
                    return client.genericKubernetesResources("serving.knative.dev/v1", "Service")
                        .inNamespace(namespace)
                        .list()
                        .toString();
                default:
                    return "Unsupported resource type: " + resource;
            }
        } catch (Exception e) {
            log.error("Error executing get command", e);
            return "Error: " + e.getMessage();
        }
    }

    private String handleDescribeCommand(KubernetesClient client, String namespace, String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 3) {
            return "Invalid describe command. Format: describe <resource> <name>";
        }

        String resource = parts[1];
        String name = parts[2];

        try {
            switch (resource.toLowerCase()) {
                case "pod":
                case "pods":
                case "po":
                    return client.pods().inNamespace(namespace).withName(name).get().toString();
                case "service":
                case "services":
                case "svc":
                    return client.services().inNamespace(namespace).withName(name).get().toString();
                case "deployment":
                case "deployments":
                case "deploy":
                    return client.apps().deployments().inNamespace(namespace).withName(name).get().toString();
                default:
                    return "Unsupported resource type for describe: " + resource;
            }
        } catch (Exception e) {
            log.error("Error executing describe command", e);
            return "Error: " + e.getMessage();
        }
    }

    private String handleLogsCommand(KubernetesClient client, String namespace, String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            return "Invalid logs command. Format: logs <pod-name> [-c container-name] [-n namespace]";
        }

        String podName = null;
        String containerNameArg = null;
        // Skip the "logs" command itself
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].equals("-n") || parts[i].equals("--namespace")) {
                // Skip the namespace parameter as it's already handled in executeCommand
                i++;
                continue;
            } else if (parts[i].equals("-c") || parts[i].equals("--container")) {
                if (i + 1 < parts.length) {
                    containerNameArg = parts[i + 1];
                    i++;
                }
            } else if (!parts[i].startsWith("-")) {
                // First non-flag argument is the pod name
                podName = parts[i];
            }
        }

        if (podName == null) {
            return "Pod name not specified. Format: logs <pod-name> [-c container-name] [-n namespace]";
        }

        try {
            // Get pod information to check containers
            var pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                return "Pod not found: " + podName;
            }

            var containers = pod.getSpec().getContainers();
            if (containers.isEmpty()) {
                return "No containers found in pod: " + podName;
            }

            // If container name is not specified and pod has multiple containers
            if (containerNameArg == null && containers.size() > 1) {
                String containerNames = containers.stream()
                    .map(container -> container.getName())
                    .collect(java.util.stream.Collectors.joining(", "));
                return "Pod has multiple containers. Please specify one using -c option. Available containers: [" + containerNames + "]";
            }

            // Determine the final container name
            final String containerName = containerNameArg != null ? containerNameArg : containers.get(0).getName();

            // Verify if specified container exists
            boolean containerExists = containers.stream()
                .anyMatch(container -> container.getName().equals(containerName));
            if (!containerExists) {
                String availableContainers = containers.stream()
                    .map(container -> container.getName())
                    .collect(java.util.stream.Collectors.joining(", "));
                return "Container '" + containerName + "' not found in pod. Available containers: [" + availableContainers + "]";
            }

            return client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .inContainer(containerName)
                .getLog();
        } catch (Exception e) {
            log.error("Error getting pod logs", e);
            return "Error getting logs: " + e.getMessage();
        }
    }

    private String handleCreateCommand(KubernetesClient client, String namespace, String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 3) {
            return "Invalid create command. Format: create <resource-type> <name> [options]";
        }

        String resourceType = parts[1].toLowerCase();
        String name = parts[2];

        try {
            switch (resourceType) {
                case "namespace":
                    return createNamespace(client, name);
                case "configmap":
                case "cm":
                    return createConfigMap(client, namespace, name, parts);
                case "secret":
                    return createSecret(client, namespace, name, parts);
                case "serviceaccount":
                case "sa":
                    return createServiceAccount(client, namespace, name);
                case "clusterrolebinding":
                case "crb":
                    return createClusterRoleBinding(client, name, parts);
                case "token":
                    return createToken(client, namespace, name);
                case "ksvc":
                case "knativeservice":
                    return createKnativeService(client, namespace, name, parts);
                default:
                    return "Unsupported resource type for create: " + resourceType;
            }
        } catch (Exception e) {
            log.error("Error executing create command", e);
            return "Error: " + e.getMessage();
        }
    }

    private String createNamespace(KubernetesClient client, String name) {
        try {
            var namespace = new io.fabric8.kubernetes.api.model.NamespaceBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .build();
            
            var result = client.namespaces().resource(namespace).create();
            return "Namespace created: " + result.getMetadata().getName();
        } catch (Exception e) {
            log.error("Error creating namespace", e);
            return "Error creating namespace: " + e.getMessage();
        }
    }

    private String createConfigMap(KubernetesClient client, String namespace, String name, String[] parts) {
        try {
            var configMapBuilder = new io.fabric8.kubernetes.api.model.ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata();

            // Parse --from-literal arguments
            for (int i = 3; i < parts.length; i++) {
                if (parts[i].equals("--from-literal") && i + 1 < parts.length) {
                    String[] keyValue = parts[i + 1].split("=", 2);
                    if (keyValue.length == 2) {
                        configMapBuilder.addToData(keyValue[0], keyValue[1]);
                    }
                    i++; // Skip the next part as we've already processed it
                }
            }

            var configMap = client.configMaps()
                .inNamespace(namespace)
                .resource(configMapBuilder.build())
                .create();
                
            return "ConfigMap created: " + configMap.getMetadata().getName();
        } catch (Exception e) {
            log.error("Error creating ConfigMap", e);
            return "Error creating ConfigMap: " + e.getMessage();
        }
    }

    private String createSecret(KubernetesClient client, String namespace, String name, String[] parts) {
        try {
            var secretBuilder = new io.fabric8.kubernetes.api.model.SecretBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withType("Opaque");

            // Parse --from-literal arguments
            for (int i = 3; i < parts.length; i++) {
                if (parts[i].equals("--from-literal") && i + 1 < parts.length) {
                    String[] keyValue = parts[i + 1].split("=", 2);
                    if (keyValue.length == 2) {
                        // Base64 encode the value for secrets
                        String encodedValue = java.util.Base64.getEncoder().encodeToString(keyValue[1].getBytes());
                        secretBuilder.addToData(keyValue[0], encodedValue);
                    }
                    i++; // Skip the next part as we've already processed it
                }
            }

            var secret = client.secrets()
                .inNamespace(namespace)
                .resource(secretBuilder.build())
                .create();
                
            return "Secret created: " + secret.getMetadata().getName();
        } catch (Exception e) {
            log.error("Error creating Secret", e);
            return "Error creating Secret: " + e.getMessage();
        }
    }

    private String createServiceAccount(KubernetesClient client, String namespace, String name) {
        try {
            var serviceAccount = new io.fabric8.kubernetes.api.model.ServiceAccountBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .build();
                
            var result = client.serviceAccounts()
                .inNamespace(namespace)
                .resource(serviceAccount)
                .create();
                
            return "ServiceAccount created: " + result.getMetadata().getName();
        } catch (Exception e) {
            log.error("Error creating ServiceAccount", e);
            return "Error creating ServiceAccount: " + e.getMessage();
        }
    }

    private String createClusterRoleBinding(KubernetesClient client, String name, String[] parts) {
        try {
            String serviceAccountName = null;
            String serviceAccountNamespace = "default";
            String clusterRole = "cluster-admin"; // 默认使用 cluster-admin 角色

            // 解析参数
            for (int i = 3; i < parts.length; i++) {
                if (parts[i].equals("--serviceaccount") && i + 1 < parts.length) {
                    String[] saInfo = parts[i + 1].split(":");
                    if (saInfo.length == 2) {
                        serviceAccountNamespace = saInfo[0];
                        serviceAccountName = saInfo[1];
                    }
                    i++;
                } else if (parts[i].equals("--clusterrole") && i + 1 < parts.length) {
                    clusterRole = parts[i + 1];
                    i++;
                }
            }

            if (serviceAccountName == null) {
                return "Error: --serviceaccount parameter is required (format: --serviceaccount namespace:serviceaccountname)";
            }

            var clusterRoleBinding = new io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withNewRoleRef()
                .withApiGroup("rbac.authorization.k8s.io")
                .withKind("ClusterRole")
                .withName(clusterRole)
                .endRoleRef()
                .addNewSubject()
                .withKind("ServiceAccount")
                .withName(serviceAccountName)
                .withNamespace(serviceAccountNamespace)
                .endSubject()
                .build();
            
            var result = client.rbac().clusterRoleBindings()
                .resource(clusterRoleBinding)
                .create();
            
            return "ClusterRoleBinding created: " + result.getMetadata().getName();
        } catch (Exception e) {
            log.error("Error creating ClusterRoleBinding", e);
            return "Error creating ClusterRoleBinding: " + e.getMessage();
        }
    }

    private String createToken(KubernetesClient client, String namespace, String serviceAccountName) {
        try {
            // 检查 ServiceAccount 是否存在
            var serviceAccount = client.serviceAccounts()
                .inNamespace(namespace)
                .withName(serviceAccountName)
                .get();
            
            if (serviceAccount == null) {
                return "Error: ServiceAccount '" + serviceAccountName + "' not found in namespace '" + namespace + "'";
            }

            // 创建一个新的 Secret 用于生成永久 Token
            var annotations = new java.util.HashMap<String, String>();
            annotations.put("kubernetes.io/service-account.name", serviceAccountName);
            // 添加不过期的注解
            annotations.put("kubernetes.io/service-account.token-expiration", "false");
            
            var tokenSecretBuilder = new io.fabric8.kubernetes.api.model.SecretBuilder()
                .withNewMetadata()
                .withGenerateName(serviceAccountName + "-token-")
                .withAnnotations(annotations)
                .addNewOwnerReference()
                .withApiVersion("v1")
                .withKind("ServiceAccount")
                .withName(serviceAccountName)
                .withUid(serviceAccount.getMetadata().getUid())
                .endOwnerReference()
                .endMetadata()
                .withType("kubernetes.io/service-account-token");

            var tokenSecret = client.secrets()
                .inNamespace(namespace)
                .resource(tokenSecretBuilder.build())
                .create();

            // 等待 Token 生成
            int maxAttempts = 10;
            int attempt = 0;
            while (attempt < maxAttempts) {
                var secret = client.secrets()
                    .inNamespace(namespace)
                    .withName(tokenSecret.getMetadata().getName())
                    .get();

                var secretData = secret != null ? secret.getData() : null;
                var token = secretData != null ? secretData.get("token") : null;
                
                if (token != null) {
                    byte[] decodedToken = java.util.Base64.getDecoder().decode(token);
                    String tokenStr = new String(decodedToken);
                    return String.format("""
                        Token created for ServiceAccount '%s':
                        %s
                        """,
                        serviceAccountName,
                        tokenStr
                    );
                }
                
                Thread.sleep(1000);
                attempt++;
            }

            return "Timeout waiting for token generation";
        } catch (Exception e) {
            log.error("Error creating token", e);
            return "Error creating token: " + e.getMessage();
        }
    }

    private String createKnativeService(KubernetesClient client, String namespace, String name, String[] parts) {
        try {
            // Check if Knative Serving CRD exists
            var knativeCrd = client.apiextensions().v1().customResourceDefinitions()
                .withName("services.serving.knative.dev")
                .get();
            
            if (knativeCrd == null) {
                return "Error: Knative Serving is not installed in the cluster. Please install Knative first.";
            }

            // Parse command line arguments
            String image = null;
            int port = 8080;  // default port
            Integer minScale = null; // default min scale
            Integer maxScale = null; // default max scale
            String env = null;
            String cpu = null;
            String memory = null;

            for (int i = 0; i < parts.length; i++) {
                switch (parts[i]) {
                    case "--image":
                        if (i + 1 < parts.length) {
                            image = parts[++i];
                        }
                        break;
                    case "--port":
                        if (i + 1 < parts.length) {
                            port = Integer.parseInt(parts[++i]);
                        }
                        break;
                    case "--min-scale":
                        if (i + 1 < parts.length) {
                            minScale = Integer.parseInt(parts[++i]);
                        }
                        break;
                    case "--max-scale":
                        if (i + 1 < parts.length) {
                            maxScale = Integer.parseInt(parts[++i]);
                        }
                        break;
                    case "--env":
                        if (i + 1 < parts.length) {
                            env = parts[++i];
                        }
                        break;
                    case "--cpu":
                        if (i + 1 < parts.length) {
                            cpu = parts[++i];
                        }
                        break;
                    case "--memory":
                        if (i + 1 < parts.length) {
                            memory = parts[++i];
                        }
                        break;
                }
            }

            if (image == null) {
                return "Error: --image parameter is required";
            }

            // Build the Knative Service specification
            Map<String, Object> serviceSpec = new HashMap<>();
            
            // Create metadata
            ObjectMeta metadata = new ObjectMeta();
            metadata.setName(name);
            metadata.setNamespace(namespace);

            Map<String, Object> template = new HashMap<>();
            Map<String, Object> templateSpec = new HashMap<>();
            Map<String, Object> container = new HashMap<>();

            container.put("image", image);
            
            // Add container port if specified
            if (port > 0) {
                Map<String, Object> containerPort = new HashMap<>();
                containerPort.put("containerPort", port);
                container.put("ports", Collections.singletonList(containerPort));
            }

            // Add environment variables if specified
            if (env != null) {
                List<Map<String, Object>> envVars = new ArrayList<>();
                String[] envPairs = env.split(",");
                for (String pair : envPairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        Map<String, Object> envVar = new HashMap<>();
                        envVar.put("name", keyValue[0]);
                        envVar.put("value", keyValue[1]);
                        envVars.add(envVar);
                    }
                }
                container.put("env", envVars);
            }

            // Add resource requirements if specified
            if (cpu != null || memory != null) {
                Map<String, Object> resources = new HashMap<>();
                Map<String, Object> limits = new HashMap<>();
                if (cpu != null) limits.put("cpu", cpu);
                if (memory != null) limits.put("memory", memory);
                resources.put("limits", limits);
                resources.put("requests", limits);  // Use same values for requests
                container.put("resources", resources);
            }

            templateSpec.put("containers", Collections.singletonList(container));
            template.put("spec", templateSpec);

            // Handle annotations for autoscaling
            if (minScale != null || maxScale != null) {
                Map<String, String> annotations = new HashMap<>();
                if (minScale != null) {
                    annotations.put("autoscaling.knative.dev/minScale", String.valueOf(minScale));
                }
                if (maxScale != null) {
                    annotations.put("autoscaling.knative.dev/maxScale", String.valueOf(maxScale));
                }
                metadata.setAnnotations(annotations);
            }

            serviceSpec.put("template", template);

            // Create the Knative Service using GenericKubernetesResource
            GenericKubernetesResource service = new GenericKubernetesResource();
            service.setKind("Service");
            service.setApiVersion("serving.knative.dev/v1");
            service.setMetadata(metadata);
            service.setAdditionalProperties(serviceSpec);

            // Create the service
            var result = client.genericKubernetesResources("serving.knative.dev/v1", "Service")
                .inNamespace(namespace)
                .resource(service)
                .create();

            return "Successfully created Knative Service: " + name;
        } catch (KubernetesClientException e) {
            log.error("Failed to create Knative Service: {}", e.getMessage());
            return "Failed to create Knative Service: " + e.getMessage();
        }
    }

    private String handleDeleteCommand(KubernetesClient client, String namespace, String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 3) {
            return "Invalid delete command. Format: delete <resource-type> <name>";
        }

        String resourceType = parts[1].toLowerCase();
        String name = parts[2];

        try {
            switch (resourceType) {
                case "pod":
                case "pods":
                case "po":
                    client.pods().inNamespace(namespace).withName(name).delete();
                    return "Pod deleted: " + name;
                case "namespace":
                    client.namespaces().withName(name).delete();
                    return "Namespace deleted: " + name;
                case "configmap":
                case "cm":
                    client.configMaps().inNamespace(namespace).withName(name).delete();
                    return "ConfigMap deleted: " + name;
                case "secret":
                    client.secrets().inNamespace(namespace).withName(name).delete();
                    return "Secret deleted: " + name;
                case "serviceaccount":
                case "sa":
                    client.serviceAccounts().inNamespace(namespace).withName(name).delete();
                    return "ServiceAccount deleted: " + name;
                case "clusterrolebinding":
                case "crb":
                    client.rbac().clusterRoleBindings().withName(name).delete();
                    return "ClusterRoleBinding deleted: " + name;
                case "ksvc":
                case "knativeservice":
                    client.genericKubernetesResources("serving.knative.dev/v1", "Service")
                        .inNamespace(namespace)
                        .withName(name)
                        .delete();
                    return "Knative Service deleted: " + name;
                default:
                    return "Unsupported resource type for delete: " + resourceType;
            }
        } catch (Exception e) {
            log.error("Error executing delete command", e);
            return "Error: " + e.getMessage();
        }
    }
}

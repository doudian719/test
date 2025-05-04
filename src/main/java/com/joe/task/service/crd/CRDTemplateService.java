package com.joe.task.service.crd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.joe.task.model.crd.ClusterFlowParameters;
import com.joe.task.model.crd.ClusterOutputParameters;
import com.joe.task.service.k8s.CRDService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CRDTemplateService {
    private final CRDService crdService;
    private final ObjectMapper objectMapper;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public CRDTemplateService(CRDService crdService) {
        this.crdService = crdService;
        this.objectMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Create ClusterFlow CRD instance
     */
    public Map<String, Object> createClusterFlow(
            String env,
            String namespace,
            ClusterFlowParameters parameters) {
        
        // 1. Validate parameters
        validateClusterFlowParameters(parameters);
        
        // 2. Get and process template
        String template = getTemplate("ClusterFlow");
        String processedTemplate = processClusterFlowTemplate(template, parameters);
        
        // 3. Convert to Map
        try {
            Map<String, Object> instance = objectMapper.readValue(processedTemplate, Map.class);
            
            // 4. Create instance
            return crdService.createCRDInstance(env, "clusterflows.logging.banzaicloud.io", namespace, instance);
        } catch (JsonProcessingException e) {
            log.error("Failed to process template", e);
            throw new RuntimeException("Failed to process template", e);
        }
    }

    /**
     * Create ClusterOutput CRD instance
     */
    public Map<String, Object> createClusterOutput(
            String env,
            String namespace,
            ClusterOutputParameters parameters) {
        
        // 1. Validate parameters
        validateClusterOutputParameters(parameters);
        
        // 2. Get and process template
        String template = getTemplate("ClusterOutput");
        String processedTemplate = processClusterOutputTemplate(template, parameters);
        
        // 3. Convert to Map
        try {
            Map<String, Object> instance = objectMapper.readValue(processedTemplate, Map.class);
            
            // 4. Create instance
            return crdService.createCRDInstance(env, "clusteroutputs.logging.banzaicloud.io", namespace, instance);
        } catch (JsonProcessingException e) {
            log.error("Failed to process template", e);
            throw new RuntimeException("Failed to process template", e);
        }
    }

    private void validateClusterFlowParameters(ClusterFlowParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("ClusterFlow parameters cannot be null");
        }
        if (StringUtils.isEmpty(parameters.getName())) {
            throw new IllegalArgumentException("ClusterFlow name cannot be empty");
        }
        if (parameters.getNamespaces() == null || parameters.getNamespaces().isEmpty()) {
            throw new IllegalArgumentException("ClusterFlow namespaces cannot be empty");
        }
    }

    private void validateClusterOutputParameters(ClusterOutputParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("ClusterOutput parameters cannot be null");
        }
        if (StringUtils.isEmpty(parameters.getName())) {
            throw new IllegalArgumentException("ClusterOutput name cannot be empty");
        }
        if (StringUtils.isEmpty(parameters.getNamespace())) {
            throw new IllegalArgumentException("ClusterOutput namespace cannot be empty");
        }
        if (StringUtils.isEmpty(parameters.getDefaultTopic())) {
            throw new IllegalArgumentException("ClusterOutput defaultTopic cannot be empty");
        }
    }

    String getTemplate(String templateName) {
        return templateCache.computeIfAbsent(templateName, this::loadTemplate);
    }

    private String loadTemplate(String templateName) {
        try {
            Path templatePath = Path.of("src", "main", "resources", "crd-templates", templateName + ".yaml");
            return new String(Files.readAllBytes(templatePath));
        } catch (IOException e) {
            log.error("Failed to load template: {}", templateName, e);
            throw new RuntimeException("Failed to load template: " + templateName, e);
        }
    }

    String processClusterFlowTemplate(String template, ClusterFlowParameters parameters) {
        // Replace name
        template = template.replace("kube-system-logs", parameters.getName());
        
        // Replace namespaces
        String namespacesYaml = parameters.getNamespaces().stream()
            .map(ns -> "          - " + ns)
            .collect(Collectors.joining("\n"));
        template = template.replace("          - kube-system", namespacesYaml);
        
        return template;
    }

    String processClusterOutputTemplate(String template, ClusterOutputParameters parameters) {
        // Replace name
        template = template.replace("kafka-cluster-output", parameters.getName());
        
        // Replace namespace
        template = template.replace("cattle-logging-system", parameters.getNamespace());
        
        // Replace default_topic
        template = template.replace("my-topic", parameters.getDefaultTopic());
        
        return template;
    }
} 
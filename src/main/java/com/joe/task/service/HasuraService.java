package com.joe.task.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.joe.task.dto.CreateSchemaDto;
import com.joe.task.dto.HasuraRequestDto;
import com.joe.task.entity.EnvConfig;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class HasuraService
{
    private static final String RESOURCE_TYPE = "Hasura";
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 30;
    private static final String METADATA_ENDPOINT = "/v1/metadata";
    
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final EnvConfigService envConfigService;

    @Autowired
    public HasuraService(EnvConfigService envConfigService)
    {
        this.envConfigService = envConfigService;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate()
    {
        try
        {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = (hostname, session) -> true;

            // Create request factory with custom SSL settings
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout((int) Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS).toMillis());
            requestFactory.setReadTimeout((int) Duration.ofSeconds(READ_TIMEOUT_SECONDS).toMillis());
            
            // Set the default SSL socket factory
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            return new RestTemplateBuilder()
                .requestFactory(() -> requestFactory)
                .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSL context", e);
        }
    }
    
    private String formatSchemaName(String namespace, String serviceName) {
        return "_" + namespace.replace("-", "_") + "_" + serviceName.replace("-", "_");
    }
    
    private String formatServiceUrl(String namespace, String serviceName) {
        return String.format("http://%s.%s.svc.cluster.local", serviceName, namespace);
    }
    
    private String populateRootNamespace(String namespace, String serviceName) {
        return "_" + namespace.replace("-", "_") + "_" + serviceName.replace("-", "_");
    }
    
    private String executeMetadataRequest(String env, HasuraRequestDto requestDto) {
        HttpHeaders headers = createAuthHeaders(env);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<HasuraRequestDto> request = new HttpEntity<>(requestDto, headers);

        EnvConfig envConfig = getEnvConfig(env);
        
        return restTemplate.postForObject(envConfig.getServerUrl() + METADATA_ENDPOINT, request, String.class);
    }

    private EnvConfig getEnvConfig(String env) {
        EnvConfig envConfig = envConfigService.getConfigByEnvAndResourceType(env, RESOURCE_TYPE);
        if (envConfig == null || envConfig.getServerUrl() == null) {
            throw new IllegalArgumentException("No configuration found for environment: " + env);
        }
        return envConfig;
    }
    
    private CreateSchemaDto populateCreateSchemaDto(String namespace, String serviceName) {
        return CreateSchemaDto.builder()
            .pluginNamespace(namespace)
            .functionName(serviceName)
            .prefix(serviceName.replace("-", "_"))
            .rootNamespace(populateRootNamespace(namespace, serviceName))
            .build();
    }
    
    private boolean isResponseSuccessful(String response) {
        if (response == null) {
            return false;
        }
        try {
            JsonNode responseNode = objectMapper.readTree(response);
            // Check if response contains error
            if (responseNode.has("error")) {
                log.error("Hasura API error: {}", responseNode.get("error").asText());
                return false;
            }
            // Check for success message
            if (responseNode.has("message") && "success".equals(responseNode.get("message").asText())) {
                return true;
            }
            // If no success message, check for code
            if (responseNode.has("code") && !responseNode.get("code").asText().equals("success")) {
                log.error("Hasura API error code: {}", responseNode.get("code").asText());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to parse Hasura API response: {}", response, e);
            return false;
        }
    }

    public boolean addRemoteSchema(String env, String namespace, String serviceName) {
        try {
            CreateSchemaDto createSchemaDto = populateCreateSchemaDto(namespace, serviceName);
            String rootNamespace = populateRootNamespace(namespace, serviceName);

            HasuraRequestDto reloadSchema = HasuraRequestDto.builder()
                .type("add_remote_schema")
                .args(ImmutableMap.of(
                    "name", rootNamespace,
                    "definition", ImmutableMap.of(
                        "url", formatServiceUrl(namespace, serviceName) + "/graphql",
                        "forward_client_headers", true,
                        "timeout_seconds", CONNECT_TIMEOUT_SECONDS,
                        "customization", ImmutableMap.of(
                            "root_fields_namespace", rootNamespace,
                            "type_names", ImmutableMap.of(
                                "prefix", createSchemaDto.getPrefix()
                            )
                        )
                    )
                ))
                .build();
            
            String response = executeMetadataRequest(env, reloadSchema);
            return isResponseSuccessful(response);
        } catch (Exception e) {
            log.error("Failed to create remote schema for namespace {} and service {} in env {}", 
                     namespace, serviceName, env, e);
            return false;
        }
    }

    public boolean deleteRemoteSchema(String env, String namespace, String serviceName) {
        try {
            String schemaName = formatSchemaName(namespace, serviceName);
            return deleteRemoteSchema(env, schemaName);
        } catch (Exception e) {
            log.error("Failed to delete remote schema for namespace {} and service {} in env {}", 
                     namespace, serviceName, env, e);
            return false;
        }
    }
    
    public boolean deleteRemoteSchema(String env, String schemaName) {
        log.info("Deleting remote schema {} in env {}", schemaName, env);
        try {
            HasuraRequestDto requestDto = HasuraRequestDto.builder()
                .type("remove_remote_schema")
                .args(ImmutableMap.of("name", schemaName))
                .build();
            
            String response = executeMetadataRequest(env, requestDto);
            return isResponseSuccessful(response);
        } catch (Exception e) {
            log.error("Failed to delete remote schema {} in env {}", schemaName, env, e);
            return false;
        }
    }

    public List<RemoteSchema> listRemoteSchemas(String env, String keyword) {
        log.info("Listing all remote schemas in env {}", env);
        try {
            HasuraRequestDto requestDto = HasuraRequestDto.builder()
                .type("export_metadata")
                .args(ImmutableMap.of())
                .build();
            String response = executeMetadataRequest(env, requestDto);
            JsonNode metadata = objectMapper.readTree(response);
            List<RemoteSchema> schemas = new ArrayList<>();
            JsonNode remoteSchemasNode = metadata.path("remote_schemas");
            if (remoteSchemasNode.isArray()) {
                for (JsonNode schemaNode : remoteSchemasNode) {
                    if (StringUtils.isNotBlank(keyword) && 
                        !StringUtils.containsIgnoreCase(schemaNode.get("name").asText(), keyword)) {
                        continue;
                    }
                    schemas.add(buildRemoteSchema(schemaNode));
                }
            }
            // Enrich with schema health status, only call getInconsistentRemoteSchemas once
            List<JsonNode> inconsistentSchemas = getInconsistentRemoteSchemas(env);
            for (RemoteSchema schema : schemas) {
                schema.setHealthStatus(getSchemaHealthStatus(env, schema.getName(), inconsistentSchemas));
            }
            return schemas;
        } catch (Exception e) {
            log.error("Failed to list remote schemas in env {}", env, e);
            return Collections.emptyList();
        }
    }

    private RemoteSchema buildRemoteSchema(JsonNode schemaNode) {
        return RemoteSchema.builder()
            .name(schemaNode.get("name").asText())
            .url(schemaNode.path("definition").path("url").asText())
            .timeoutSeconds(schemaNode.path("definition").path("timeout_seconds").asInt())
            .forwardClientHeaders(schemaNode.path("definition").path("forward_client_headers").asBoolean())
            .comments(schemaNode.path("comment").asText())
            .build();
    }

    private List<JsonNode> getInconsistentRemoteSchemas(String env) {
        try {
            HasuraRequestDto requestDto = HasuraRequestDto.builder()
                    .type("get_inconsistent_metadata")
                    .args(ImmutableMap.of())
                    .build();
            String response = executeMetadataRequest(env, requestDto);
            JsonNode result = objectMapper.readTree(response);
            List<JsonNode> inconsistentSchemas = new ArrayList<>();
            JsonNode objects = result.path("inconsistent_objects");
            if (objects.isArray()) {
                for (JsonNode obj : objects) {
                    if ("remote_schema".equals(obj.path("type").asText())) {
                        inconsistentSchemas.add(obj);
                    }
                }
            }
            return inconsistentSchemas;
        } catch (Exception e) {
            log.error("Failed to get inconsistent remote schemas in env {}", env, e);
            return Collections.emptyList();
        }
    }

    public static SchemaHealthStatus getSchemaHealthStatus(String env, String schemaName, List<JsonNode> inconsistentSchemas) {
        for (JsonNode obj : inconsistentSchemas) {
            String name = obj.path("definition").path("name").asText("");
            if (schemaName.equals(name)) {
                String reason = obj.path("reason").asText("");
                String details = obj.path("message").path("message").asText("");
                return new SchemaHealthStatus(schemaName, false, reason + ": " + details);
            }
        }
        return new SchemaHealthStatus(schemaName, true, "");
    }

    public List<SchemaHealthStatus> checkAllRemoteSchemasHealth(String env) {
        log.info("Checking health status for all remote schemas in env {}", env);
        try {
            List<RemoteSchema> schemas = listRemoteSchemas(env, null);
            List<JsonNode> inconsistentSchemas = getInconsistentRemoteSchemas(env);
            List<SchemaHealthStatus> healthStatuses = new ArrayList<>();
            for (RemoteSchema schema : schemas) {
                healthStatuses.add(getSchemaHealthStatus(env, schema.getName(), inconsistentSchemas));
            }
            return healthStatuses;
        } catch (Exception e) {
            log.error("Failed to check remote schemas health in env {}", env, e);
            return Collections.emptyList();
        }
    }

    private HttpHeaders createAuthHeaders(String env) {
        HttpHeaders headers = new HttpHeaders();
        EnvConfig envConfig = getEnvConfig(env);
        if (envConfig.getToken() != null) {
            headers.set("X-Hasura-Admin-Secret", envConfig.getToken());
        }
        return headers;
    }
    
    @Data
    @AllArgsConstructor
    @Builder
    public static class RemoteSchema {
        private final String name;
        private final String url;
        private int timeoutSeconds;
        private boolean forwardClientHeaders;
        private String comments;
        private SchemaHealthStatus healthStatus;
    }
    
    @Data
    @AllArgsConstructor
    @Builder
    public static class SchemaHealthStatus {
        private final String name;
        private final boolean healthy;
        private final String details;
    }

    public boolean refreshRemoteSchema(String env, String schemaName) {
        log.info("Refreshing remote schema {} in env {}", schemaName, env);
        try {
            HasuraRequestDto requestDto = HasuraRequestDto.builder()
                .type("reload_remote_schema")
                .args(ImmutableMap.of("name", schemaName))
                .build();
            String response = executeMetadataRequest(env, requestDto);
            return isResponseSuccessful(response);
        } catch (Exception e) {
            log.error("Failed to refresh remote schema {} in env {}", schemaName, env, e);
            return false;
        }
    }
}

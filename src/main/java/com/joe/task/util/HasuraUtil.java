package com.joe.task.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.joe.task.config.HasuraProperties;
import com.joe.task.dto.CreateSchemaDto;
import com.joe.task.dto.HasuraRequestDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class HasuraUtil
{
    private static ObjectMapper objectMapper;
    private static HasuraProperties properties;
    private static RestTemplate restTemplate;
    
    private HasuraUtil() {
        // Private constructor to prevent instantiation
    }
    
    public static void init(HasuraProperties hasuraProperties) {
        properties = hasuraProperties;
        objectMapper = new ObjectMapper();
        
        try {
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
            requestFactory.setConnectTimeout((int) Duration.ofSeconds(properties.getConnectTimeout()).toMillis());
            requestFactory.setReadTimeout((int) Duration.ofSeconds(properties.getReadTimeout()).toMillis());
            
            // Set the default SSL socket factory
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            // Create RestTemplate with our custom settings
            restTemplate = new RestTemplateBuilder()
                .requestFactory(() -> requestFactory)
                .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSL context", e);
        }
    }
    
    private static String formatSchemaName(String namespace, String serviceName) {
        String formattedNamespace = namespace.replace("-", "_");
        String formattedServiceName = serviceName.replace("-", "_");
        return "_" + formattedNamespace + "_" + formattedServiceName;
    }
    
    private static String formatServiceUrl(String namespace, String serviceName) {
        return String.format("http://%s.%s.svc.cluster.local", serviceName, namespace);
    }
    
    private static String populateRootNamespace(String namespace, String serviceName) {
        return "_" + namespace.replace("-", "_") + "_" + serviceName.replace("-", "_");
    }
    
    private static String executeMetadataRequest(String env, HasuraRequestDto requestDto) {
        HttpHeaders headers = createAuthHeaders(env);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<HasuraRequestDto> request = new HttpEntity<>(requestDto, headers);
        
        HasuraProperties.EnvironmentConfig envConfig = properties.getEnvironments().get(env);
        if (envConfig == null || envConfig.getUrl() == null) {
            throw new IllegalArgumentException("No configuration found for environment: " + env);
        }
        
        return restTemplate.postForObject(envConfig.getUrl() + "/v1/metadata", request, String.class);
    }
    
    private static CreateSchemaDto populateCreateSchemaDto(String namespace, String serviceName) {
        return CreateSchemaDto.builder()
            .pluginNamespace(namespace)
            .functionName(serviceName)
            .prefix(StringUtils.replaceAll(serviceName, "-", "_"))
            .rootNamespace(populateRootNamespace(namespace, serviceName))
            .build();
    }
    
    public static boolean addRemoteSchema(String env, String namespace, String serviceName) {
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
                        "timeout_seconds", 30,
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
            return response != null;
        } catch (Exception e) {
            log.error("Failed to create remote schema for namespace {} and service {} in env {}", 
                     namespace, serviceName, env, e);
            return false;
        }
    }

    public static boolean deleteRemoteSchema(String env, String namespace, String serviceName) {
        try {
            String schemaName = formatSchemaName(namespace, serviceName);
            HasuraRequestDto requestDto = HasuraRequestDto.builder()
                .type("remove_remote_schema")
                .args(ImmutableMap.of("name", schemaName))
                .build();
            
            String response = executeMetadataRequest(env, requestDto);
            return response != null;
        } catch (Exception e) {
            log.error("Failed to delete remote schema for namespace {} and service {} in env {}", 
                     namespace, serviceName, env, e);
            return false;
        }
    }
    
    public static boolean deleteRemoteSchema(String env, String schemaName) {
        log.info("Deleting remote schema {} in env {}", schemaName, env);
        try {
            HasuraRequestDto requestDto = HasuraRequestDto.builder()
                .type("remove_remote_schema")
                .args(ImmutableMap.of("name", schemaName))
                .build();
            
            String response = executeMetadataRequest(env, requestDto);
            return response != null;
        } catch (Exception e) {
            log.error("Failed to delete remote schema {} in env {}", schemaName, env, e);
            return false;
        }
    }

    public static List<RemoteSchema> listRemoteSchemas(String env, String keyword)
    {
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
            
            if (remoteSchemasNode.isArray())
            {
                for (JsonNode schemaNode : remoteSchemasNode)
                {
                    if(StringUtils.isNotBlank(keyword))
                    {
                        if(!StringUtils.containsIgnoreCase(schemaNode.get("name").asText(), keyword))
                        {
                            continue;
                        }
                    }

                    schemas.add(RemoteSchema.builder()
                            .name(schemaNode.get("name").asText())
                            .url(schemaNode.path("definition").path("url").asText())
                            .timeoutSeconds(schemaNode.path("definition").path("timeout_seconds").asInt())
                            .forwardClientHeaders(schemaNode.path("definition").path("forward_client_headers").asBoolean())
                            .comments(schemaNode.path("comment").asText())
                            .build());
                }
            }

            // Enrich with schema health status
            for (RemoteSchema schema : schemas)
            {
                schema.setHealthStatus(getSchemaHealthStatus(env, schema.getName()));
            }

            return schemas;
        } catch (Exception e) {
            log.error("Failed to list remote schemas in env {}", env, e);
            return Collections.emptyList();
        }
    }

    public static SchemaHealthStatus getSchemaHealthStatus(String env, String schemaName) {
        log.info("Checking health of remote schema {} in env {}", schemaName, env);
        try {
            HasuraRequestDto requestDto = HasuraRequestDto.builder()
                    .type("get_remote_schema_health")
                    .args(ImmutableMap.of("name", schemaName))
                    .build();

            String response = executeMetadataRequest(env, requestDto);
            JsonNode healthStatus = objectMapper.readTree(response);

            boolean isHealthy = healthStatus.path("status").asText("unhealthy").equals("healthy");
            String details = "";

            if (!isHealthy) {
                JsonNode detailsNode = healthStatus.path("details");
                if (!detailsNode.isMissingNode()) {
                    details = detailsNode.toString();
                } else {
                    details = healthStatus.toString();
                }
            }

            return new SchemaHealthStatus(schemaName, isHealthy, details);
        } catch (Exception e) {
            log.error("Failed to check health of remote schema {} in env {}", schemaName, env, e);
            return new SchemaHealthStatus(schemaName, false, "Error checking health status: " + e.getMessage());
        }
    }

    public static List<SchemaHealthStatus> checkAllRemoteSchemasHealth(String env)
    {
        log.info("Checking health status for all remote schemas in env {}", env);
        List<SchemaHealthStatus> healthStatuses = new ArrayList<>();
        
        try
        {
            List<RemoteSchema> schemas = listRemoteSchemas(env, null);
            
            for (RemoteSchema schema : schemas)
            {
                healthStatuses.add(getSchemaHealthStatus(env, schema.getName()));
            }
        }
        catch (Exception e)
        {
            log.error("Failed to list remote schemas in env {}", env, e);
            return Collections.emptyList();
        }
        
        return healthStatuses;
    }

    private static HttpHeaders createAuthHeaders(String env) {
        HttpHeaders headers = new HttpHeaders();
        
        HasuraProperties.EnvironmentConfig envConfig = properties.getEnvironments().get(env);
        if (envConfig != null && envConfig.getAdminSecret() != null) {
            headers.set("X-Hasura-Admin-Secret", envConfig.getAdminSecret());
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

    public static boolean refreshRemoteSchema(String env, String schemaName) {
        log.info("Refreshing remote schema {} in env {}", schemaName, env);
        try {
            HasuraRequestDto requestDto = HasuraRequestDto.builder()
                .type("reload_remote_schema")
                .args(ImmutableMap.of("name", schemaName))
                .build();
            String response = executeMetadataRequest(env, requestDto);
            return response != null;
        } catch (Exception e) {
            log.error("Failed to refresh remote schema {} in env {}", schemaName, env, e);
            return false;
        }
    }
}

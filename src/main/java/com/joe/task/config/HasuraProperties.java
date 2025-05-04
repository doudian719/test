package com.joe.task.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;
import java.util.Map;
import java.util.HashMap;

@Data
@Component
@ConfigurationProperties(prefix = "hasura")
public class HasuraProperties {
    
    // Default timeout settings (in seconds)
    private int connectTimeout = 30;
    private int readTimeout = 30;
    
    // Environment specific configurations
    private Map<String, EnvironmentConfig> environments = new HashMap<>();
    
    // Authentication settings
    private Map<String, Auth> auth = new HashMap<>();
    
    // Cache settings
    private Cache cache = new Cache();
    
    // Version control settings
    private VersionControl versionControl = new VersionControl();
    
    @Data
    public static class EnvironmentConfig {
        private String url;           // Hasura endpoint URL
        private String adminSecret;   // Admin secret for authentication
    }
    
    @Data
    public static class Auth {
        private String adminSecret;
        private String jwt;
        private Map<String, String> headers = new HashMap<>();
    }
    
    @Data
    public static class Cache {
        private boolean enabled = true;
        private int timeToLiveSeconds = 300;
        private int maxSize = 1000;
    }
    
    @Data
    public static class VersionControl {
        private boolean enabled = true;
        private String storageType = "file"; // file or database
        private String storagePath = "hasura-migrations";
        private boolean autoApplyMigrations = false;
    }
} 
package com.joe.task.service;

import com.joe.task.config.CacheConfig;
import com.joe.task.entity.EnvConfig;
import com.joe.task.entity.ResourceType;
import com.joe.task.repo.EnvConfigRepository;
import com.joe.task.repository.ResourceTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@DependsOn("resourceTypeService")
public class EnvConfigService {
    private final EnvConfigRepository envConfigRepository;
    private final ResourceTypeRepository resourceTypeRepository;

    @Autowired
    public EnvConfigService(EnvConfigRepository envConfigRepository, 
                          ResourceTypeRepository resourceTypeRepository) {
        this.envConfigRepository = envConfigRepository;
        this.resourceTypeRepository = resourceTypeRepository;
        log.info("EnvConfigService initialized");
    }
    
    @PostConstruct
    public void init() {
        log.info("Initializing EnvConfigService...");
        if (envConfigRepository.count() == 0) {
            log.info("No environment configurations found, creating default configurations");
            ResourceType resourceType = null;
            if(resourceTypeRepository.existsById(1L)) {
                resourceType = resourceTypeRepository.getReferenceById(1L);
                log.debug("Found resource type with id 1");
            } else {
                log.warn("Resource type with id 1 not found");
            }

            String sitToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IkgyaG5GYV9FNGozLUZ6S1VaRnJIck1JV09uS1FzbFlhZjVBTGVZZ19udFUifQ.eyJhdWQiOlsiaHR0cHM6Ly9rdWJlcm5ldGVzLmRlZmF1bHQuc3ZjLmNsdXN0ZXIubG9jYWwiXSwiZXhwIjoxNzQxNDI4MzQ5LCJpYXQiOjE3NDE0MjQ3NDksImlzcyI6Imh0dHBzOi8va3ViZXJuZXRlcy5kZWZhdWx0LnN2Yy5jbHVzdGVyLmxvY2FsIiwianRpIjoiMTZjNTE3NmYtODQzMS00ZGFkLWFiN2EtNzQ0NWZjZmU3YmRkIiwia3ViZXJuZXRlcy5pbyI6eyJuYW1lc3BhY2UiOiJrdWJlLXN5c3RlbSIsInNlcnZpY2VhY2NvdW50Ijp7Im5hbWUiOiJhZG1pbiIsInVpZCI6IjJjODEyZGI0LTcwMTctNDUxZC1hOWJiLTRlMThlMzA2MDRhMyJ9fSwibmJmIjoxNzQxNDI0NzQ5LCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a3ViZS1zeXN0ZW06YWRtaW4ifQ.ESFCfZ1SPP8JRXZT0XoDUNjhZz48q-ocLsd049bw81QH3BpekN0ssj-rWfZS3wgnqlIPbR1aZ4feiU-pXmnwd2hkRMnnc52Q24Me3sr9vMTPM78oA-BMWLwCR73CYD1CwESVzjE3UNKgIuAHyM4eMWrgNatnsTUnrxwG8bw_dKCYpvHAlv0-UPlbzh_yJfXGs9H-t_6nF8L8PdjoSeLjFbDywX23zts3n-Vo0LFB2URttUMtisqJd03ZMEbiTEZFhLLMbMARNyXoZzBLNm3-j29bAqOoqs9npii7uJuj17gC3gPnXw_vLL7jB_vT_XwAdZN58goiELKpUC_hv5DOxg";
            
            try {
                EnvConfig sit = new EnvConfig();
                sit.setName("SIT");
                sit.setServerUrl("https://127.0.0.1:3518");
                sit.setToken(sitToken);
                sit.setSequence(1);
                sit.setIsHidden(false);
                sit.setResource(resourceType);
                envConfigRepository.save(sit);
                log.info("Created SIT environment configuration");
                
                EnvConfig uat = new EnvConfig();
                uat.setName("UAT");
                uat.setServerUrl("https://kubernetes.uat.example.com");
                uat.setToken(sitToken);
                uat.setSequence(2);
                uat.setIsHidden(false);
                uat.setResource(resourceType);
                envConfigRepository.save(uat);
                log.info("Created UAT environment configuration");
            } catch (Exception e) {
                log.error("Failed to create default environment configurations", e);
            }
        } else {
            log.info("Environment configurations already exist, skipping initialization");
        }
    }

    @Cacheable(value = CacheConfig.ENV_CONFIG_CACHE, key = "'allEnvs'")
    public List<EnvConfig> getAllEnvs() {
        log.debug("Fetching all environments");
        List<EnvConfig> envs = envConfigRepository.findAllOrderBySequence();
        log.debug("Found {} environments", envs.size());
        return envs;
    }

    @Cacheable(value = CacheConfig.ENV_CONFIG_CACHE, key = "'allVisibleEnvs'")
    public List<EnvConfig> getAllVisibleEnvs() {
        log.debug("Fetching all visible environments");
        List<EnvConfig> envs = envConfigRepository.findAllVisibleOrderBySequence();
        log.debug("Found {} visible environments", envs.size());
        return envs;
    }

    @Cacheable(value = CacheConfig.ENV_CONFIG_CACHE, key = "#id")
    public Optional<EnvConfig> getEnvById(Long id) {
        log.debug("Fetching environment with id: {}", id);
        Optional<EnvConfig> env = envConfigRepository.findById(id);
        if (env.isPresent()) {
            log.debug("Found environment: {}", env.get().getName());
        } else {
            log.debug("No environment found with id: {}", id);
        }
        return env;
    }

    @CacheEvict(value = CacheConfig.ENV_CONFIG_CACHE, allEntries = true)
    public EnvConfig saveEnv(EnvConfig envConfig) {
        log.info("Saving environment configuration: {}", envConfig.getName());
        try {
            // If sequence is not set, set it to the last position
            if (envConfig.getSequence() == null) {
                List<EnvConfig> allEnvs = envConfigRepository.findAll();
                envConfig.setSequence(allEnvs.size() + 1);
                log.debug("Set sequence to {} for environment: {}", envConfig.getSequence(), envConfig.getName());
            }
            
            // If isHidden is not set, set it to false
            if (envConfig.getIsHidden() == null) {
                envConfig.setIsHidden(false);
                log.debug("Set isHidden to false for environment: {}", envConfig.getName());
            }
            
            EnvConfig savedEnv = envConfigRepository.save(envConfig);
            log.info("Successfully saved environment: {}", savedEnv.getName());
            return savedEnv;
        } catch (Exception e) {
            log.error("Failed to save environment: {}", envConfig.getName(), e);
            throw e;
        }
    }

    @CacheEvict(value = CacheConfig.ENV_CONFIG_CACHE, allEntries = true)
    public void deleteEnv(Long id) {
        log.info("Deleting environment with id: {}", id);
        try {
            envConfigRepository.deleteById(id);
            log.info("Successfully deleted environment with id: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete environment with id: {}", id, e);
            throw e;
        }
    }

    @Cacheable(value = CacheConfig.ENV_CONFIG_CACHE, key = "'visibleEnvs_' + #resourceType")
    public List<EnvConfig> getAllVisibleEnvsByResourceType(String resourceType) {
        log.debug("Fetching visible environments for resource type: {}", resourceType);
        List<EnvConfig> envs = envConfigRepository.findAllVisibleByResourceTypeOrderBySequence(resourceType);
        log.debug("Found {} visible environments for resource type: {}", envs.size(), resourceType);
        return envs;
    }

    @Cacheable(value = CacheConfig.ENV_CONFIG_CACHE, key = "'env_' + #env + '_resource_' + #resourceType")
    public EnvConfig getConfigByEnvAndResourceType(String env, String resourceType) {
        log.debug("Fetching configuration for environment: {} and resource type: {}", env, resourceType);
        List<EnvConfig> envConfigList = envConfigRepository.findAllVisibleByResourceTypeOrderBySequence(resourceType);

        for (EnvConfig envConfig : envConfigList) {
            if(StringUtils.equalsIgnoreCase(env, envConfig.getName())) {
                log.debug("Found configuration for environment: {}", env);
                return envConfig;
            }
        }

        log.warn("No configuration found for environment: {} and resource type: {}", env, resourceType);
        return null;
    }
} 
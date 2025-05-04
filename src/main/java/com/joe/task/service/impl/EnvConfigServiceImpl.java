package com.joe.task.service.impl;

import com.joe.task.config.CacheConfig;
import com.joe.task.entity.EnvConfig;
import com.joe.task.repo.EnvConfigRepository;
import com.joe.task.service.EnvConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

@Service
public class EnvConfigServiceImpl implements EnvConfigService {

    private final EnvConfigRepository envConfigRepository;

    @Autowired
    public EnvConfigServiceImpl(EnvConfigRepository envConfigRepository) {
        this.envConfigRepository = envConfigRepository;
    }
    
    @PostConstruct
    public void init() {
        if (envConfigRepository.count() == 0) {
            // 从命令行获取的token
            String sitToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IkgyaG5GYV9FNGozLUZ6S1VaRnJIck1JV09uS1FzbFlhZjVBTGVZZ19udFUifQ.eyJhdWQiOlsiaHR0cHM6Ly9rdWJlcm5ldGVzLmRlZmF1bHQuc3ZjLmNsdXN0ZXIubG9jYWwiXSwiZXhwIjoxNzQxNDI4MzQ5LCJpYXQiOjE3NDE0MjQ3NDksImlzcyI6Imh0dHBzOi8va3ViZXJuZXRlcy5kZWZhdWx0LnN2Yy5jbHVzdGVyLmxvY2FsIiwianRpIjoiMTZjNTE3NmYtODQzMS00ZGFkLWFiN2EtNzQ0NWZjZmU3YmRkIiwia3ViZXJuZXRlcy5pbyI6eyJuYW1lc3BhY2UiOiJrdWJlLXN5c3RlbSIsInNlcnZpY2VhY2NvdW50Ijp7Im5hbWUiOiJhZG1pbiIsInVpZCI6IjJjODEyZGI0LTcwMTctNDUxZC1hOWJiLTRlMThlMzA2MDRhMyJ9fSwibmJmIjoxNzQxNDI0NzQ5LCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a3ViZS1zeXN0ZW06YWRtaW4ifQ.ESFCfZ1SPP8JRXZT0XoDUNjhZz48q-ocLsd049bw81QH3BpekN0ssj-rWfZS3wgnqlIPbR1aZ4feiU-pXmnwd2hkRMnnc52Q24Me3sr9vMTPM78oA-BMWLwCR73CYD1CwESVzjE3UNKgIuAHyM4eMWrgNatnsTUnrxwG8bw_dKCYpvHAlv0-UPlbzh_yJfXGs9H-t_6nF8L8PdjoSeLjFbDywX23zts3n-Vo0LFB2URttUMtisqJd03ZMEbiTEZFhLLMbMARNyXoZzBLNm3-j29bAqOoqs9npii7uJuj17gC3gPnXw_vLL7jB_vT_XwAdZN58goiELKpUC_hv5DOxg";
            
            EnvConfig sit = new EnvConfig();
            sit.setName("SIT");
            sit.setK8sServerHost("https://127.0.0.1:3518");
            sit.setK8sToken(sitToken);
            sit.setSequence(1);
            sit.setIsHidden(false);
            envConfigRepository.save(sit);
            
            EnvConfig uat = new EnvConfig();
            uat.setName("UAT");
            uat.setK8sServerHost("https://kubernetes.uat.example.com");
            uat.setK8sToken(sitToken); // 使用相同的token进行测试
            uat.setSequence(2);
            uat.setIsHidden(false);
            envConfigRepository.save(uat);
            
            EnvConfig prod = new EnvConfig();
            prod.setName("PROD");
            prod.setK8sServerHost("https://kubernetes.prod.example.com");
            prod.setK8sToken("sample-token-prod");
            prod.setSequence(3);
            prod.setIsHidden(true);
            envConfigRepository.save(prod);
        }
    }

    @Override
    @Cacheable(value = CacheConfig.ENV_CONFIG_CACHE, key = "'allEnvs'")
    public List<EnvConfig> getAllEnvs() {
        return envConfigRepository.findAllOrderBySequence();
    }

    @Override
    @Cacheable(value = CacheConfig.ENV_CONFIG_CACHE, key = "'allVisibleEnvs'")
    public List<EnvConfig> getAllVisibleEnvs() {
        return envConfigRepository.findAllVisibleOrderBySequence();
    }

    @Override
    @Cacheable(value = CacheConfig.ENV_CONFIG_CACHE, key = "#id")
    public Optional<EnvConfig> getEnvById(Long id) {
        return envConfigRepository.findById(id);
    }

    @Override
    @CacheEvict(value = CacheConfig.ENV_CONFIG_CACHE, allEntries = true)
    public EnvConfig saveEnv(EnvConfig envConfig) {
        // If sequence is not set, set it to the last position
        if (envConfig.getSequence() == null) {
            List<EnvConfig> allEnvs = envConfigRepository.findAll();
            envConfig.setSequence(allEnvs.size() + 1);
        }
        
        // If isHidden is not set, set it to false
        if (envConfig.getIsHidden() == null) {
            envConfig.setIsHidden(false);
        }
        
        return envConfigRepository.save(envConfig);
    }

    @Override
    @CacheEvict(value = CacheConfig.ENV_CONFIG_CACHE, allEntries = true)
    public void deleteEnv(Long id) {
        envConfigRepository.deleteById(id);
    }
} 
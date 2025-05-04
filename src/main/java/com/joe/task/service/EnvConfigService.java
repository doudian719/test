package com.joe.task.service;

import com.joe.task.entity.EnvConfig;

import java.util.List;
import java.util.Optional;

public interface EnvConfigService {
    
    /**
     * Get all environments
     * @return List of all environments
     */
    List<EnvConfig> getAllEnvs();
    
    /**
     * Get all visible environments
     * @return List of visible environments
     */
    List<EnvConfig> getAllVisibleEnvs();
    
    /**
     * Get environment by id
     * @param id Environment id
     * @return Environment
     */
    Optional<EnvConfig> getEnvById(Long id);
    
    /**
     * Save environment
     * @param envConfig Environment to save
     * @return Saved environment
     */
    EnvConfig saveEnv(EnvConfig envConfig);
    
    /**
     * Delete environment
     * @param id Environment id
     */
    void deleteEnv(Long id);
} 
package com.joe.task.cache;

import com.joe.task.entity.AdoInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Cache manager for AdoInfo
 */
@Slf4j
@Component
public class AdoInfoCache {
    
    private final CopyOnWriteArrayList<AdoInfo> cache = new CopyOnWriteArrayList<>();
    
    /**
     * Update cache with new data
     * @param adoInfoList new data
     */
    public void updateCache(List<AdoInfo> adoInfoList) {
        cache.clear();
        cache.addAll(adoInfoList);
        log.info("AdoInfo cache updated, current size: {}", cache.size());
    }
    
    /**
     * Get all repositories from cache
     * @return list of repositories
     */
    public List<AdoInfo> getAllFromCache() {
        return new ArrayList<>(cache);
    }
    
    /**
     * Search repositories by name from cache
     * @param keyword search keyword
     * @return matching repositories
     */
    public List<AdoInfo> searchFromCache(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllFromCache();
        }
        
        String lowerKeyword = keyword.toLowerCase();
        return cache.stream()
                .filter(info -> info.getRepoName() != null && 
                        info.getRepoName().toLowerCase().contains(lowerKeyword))
                .collect(Collectors.toList());
    }
    
    /**
     * Clear cache
     */
    public void clearCache() {
        cache.clear();
        log.info("AdoInfo cache cleared");
    }
    
    /**
     * Get cache size
     * @return current cache size
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Check if cache is empty
     * @return true if cache is empty
     */
    public boolean isCacheEmpty() {
        return cache.isEmpty();
    }
} 
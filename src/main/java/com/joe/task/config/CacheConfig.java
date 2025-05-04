package com.joe.task.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ENV_CONFIG_CACHE = "envConfigCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                // 设置缓存的最大容量
                .maximumSize(100)
                // 设置缓存在最后一次访问后的过期时间
                .expireAfterAccess(100, TimeUnit.MINUTES)
                // 设置缓存在最后一次写入后的过期时间
                .expireAfterWrite(100, TimeUnit.MINUTES));
        return cacheManager;
    }
} 
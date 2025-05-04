package com.joe.task.config;

import com.joe.task.entity.EnvConfig;
import com.joe.task.service.EnvConfigService;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Kubernetes客户端管理器
 * 负责创建和管理Kubernetes客户端实例
 */
@Component
public class KubernetesClientManager {
    private static final Logger log = LoggerFactory.getLogger(KubernetesClientManager.class);

    private final EnvConfigService envConfigService;
    private final Map<String, KubernetesClient> clients = new HashMap<>();
    
    // 配置是否跳过SSL证书验证
    @Value("${kubernetes.skip.ssl.verification:true}")
    private boolean skipSslVerification;
    
    // 配置是否禁用HTTP/2
    @Value("${kubernetes.disable.http2:true}")
    private boolean disableHttp2;
    
    // 配置连接超时时间（毫秒）
    @Value("${kubernetes.connection.timeout:30000}")
    private int connectionTimeout;
    
    // 配置请求超时时间（毫秒）
    @Value("${kubernetes.request.timeout:30000}")
    private int requestTimeout;

    public KubernetesClientManager(EnvConfigService envConfigService) {
        this.envConfigService = envConfigService;
    }

    /**
     * 系统启动时初始化所有环境的Kubernetes客户端
     */
    @PostConstruct
    public void initializeAllClients() {
        log.info("开始初始化所有环境的Kubernetes客户端");
        
        envConfigService.getAllVisibleEnvs().forEach(envConfig -> {
            try {
                String envName = envConfig.getName();
                log.info("正在初始化环境 {} 的Kubernetes客户端", envName);
                
                KubernetesClient client = createClient(envName);
                clients.put(envName, client);
                
                // 测试连接
                client.getApiVersion();
                log.info("环境 {} 的Kubernetes客户端初始化成功", envName);
            } catch (Exception e) {
                log.error("初始化环境 {} 的Kubernetes客户端失败", envConfig.getName(), e);
            }
        });
        
        log.info("所有环境的Kubernetes客户端初始化完成");
    }

    /**
     * 获取指定环境的Kubernetes客户端
     * @param envName 环境名称
     * @return Kubernetes客户端
     */
    public KubernetesClient getClient(String envName) {
        log.debug("获取环境 {} 的Kubernetes客户端", envName);
        
        // 每次获取客户端时，先检查是否需要刷新
        Optional<EnvConfig> envConfigOpt = envConfigService.getAllVisibleEnvs().stream()
                .filter(env -> env.getName().equalsIgnoreCase(envName))
                .findFirst();
                
        if (envConfigOpt.isEmpty()) {
            log.error("环境 {} 不存在或不可见", envName);
            throw new IllegalArgumentException("Environment not found or not visible: " + envName);
        }
        
        EnvConfig envConfig = envConfigOpt.get();
        KubernetesClient existingClient = clients.get(envName);
        
        // 如果客户端不存在，或者token已更新，则创建新的客户端
        if (existingClient == null || 
            !envConfig.getK8sToken().equals(existingClient.getConfiguration().getOauthToken())) {
            log.info("创建新的Kubernetes客户端实例，因为token已更新或客户端不存在");
            return refreshClient(envName, envConfig);
        }
        
        // 检查客户端是否仍然有效
        try {
            existingClient.getApiVersion();
            return existingClient;
        } catch (Exception e) {
            log.warn("现有客户端已失效，正在创建新的客户端", e);
            return refreshClient(envName, envConfig);
        }
    }

    /**
     * 刷新指定环境的Kubernetes客户端
     * @param envName 环境名称
     * @param envConfig 环境配置
     * @return 新的Kubernetes客户端
     */
    private KubernetesClient refreshClient(String envName, EnvConfig envConfig) {
        log.info("刷新环境 {} 的Kubernetes客户端", envName);
        
        // 关闭旧的客户端
        Optional.ofNullable(clients.get(envName)).ifPresent(KubernetesClient::close);
        
        // 创建新的客户端
        KubernetesClient newClient = createClient(envName);
        clients.put(envName, newClient);
        
        return newClient;
    }

    /**
     * 创建新的Kubernetes客户端
     * @param envName 环境名称
     * @return 新的Kubernetes客户端
     */
    private KubernetesClient createClient(String envName) {
        EnvConfig envConfig = envConfigService.getAllVisibleEnvs().stream()
                .filter(env -> env.getName().equalsIgnoreCase(envName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envName));

        Config config = new Config();
        config.setMasterUrl(envConfig.getK8sServerHost());
        config.setOauthToken(envConfig.getK8sToken());
        config.setTrustCerts(skipSslVerification);
        config.setHttp2Disable(disableHttp2);
        config.setConnectionTimeout(connectionTimeout);
        config.setRequestTimeout(requestTimeout);

        return new KubernetesClientBuilder()
                .withConfig(config)
                .build();
    }

    /**
     * 系统关闭时清理所有客户端
     */
    @PreDestroy
    public void cleanup() {
        log.info("正在清理所有Kubernetes客户端连接");
        clients.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭Kubernetes客户端时发生异常", e);
            }
        });
        clients.clear();
        log.info("所有Kubernetes客户端连接已清理完成");
    }
} 
package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import com.joe.task.dto.RichNamespaceInfo;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.NamespaceStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NamespaceService extends BaseService
{
    private final KubernetesClientManager clientManager;
    
    // 配置是否使用模拟数据
    @Value("${kubernetes.use.mock.data:true}")
    private boolean useMockData;

    public NamespaceService(KubernetesClientManager clientManager)
    {
        super(clientManager);
        this.clientManager = clientManager;
    }

    /**
     * 获取命名空间详情列表
     * @param env 环境名称
     * @param name 命名空间名称过滤条件（可选）
     * @return 命名空间详情列表
     */
    @Cacheable(value = "namespaceDetails", key = "#env")
    public List<RichNamespaceInfo> getAllNamespaceDetails(String env, String name) {
        log.debug("获取命名空间列表: env={}, name={}", env, name);
        
        int maxRetries = 3;
        int retryCount = 0;
        long retryDelay = 1000;
        
        while (retryCount < maxRetries) {
            try {
                KubernetesClient client = clientManager.getClient(env);
                
                // 测试连接
                client.getApiVersion();
                log.debug("K8s API连接正常");
                
                // 获取命名空间列表
                List<Namespace> namespaces = client.namespaces().list().getItems();
                
                // 过滤并转换命名空间信息
                List<RichNamespaceInfo> result = namespaces.stream()
                        .filter(ns -> StringUtils.isBlank(name) || StringUtils.containsIgnoreCase(ns.getMetadata().getName(), name))
                        .map(ns -> convertToRichNamespaceInfo(env, ns))
                        .collect(Collectors.toList());
                
                log.info("获取到 {} 个命名空间", result.size());
                return result;
                
            } catch (Exception e) {
                retryCount++;
                String errorMessage = e.getMessage();
                if (errorMessage.contains("401") || errorMessage.contains("Unauthorized")) {
                    log.error("认证失败 ({}/{})", retryCount, maxRetries);
                } else {
                    log.error("获取失败 ({}/{}): {}", retryCount, maxRetries, errorMessage);
                }
                
                if (retryCount < maxRetries) {
                    log.debug("等待 {}ms 后重试", retryDelay);
                    try {
                        Thread.sleep(retryDelay);
                        retryDelay *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                } else {
                    throw new RuntimeException("获取命名空间列表失败: " + errorMessage, e);
                }
            }
        }
        
        throw new RuntimeException("获取命名空间列表失败：达到最大重试次数");
    }

    /**
     * 创建模拟命名空间数据
     * @return 模拟命名空间列表
     */
    private List<Namespace> createMockNamespaces() {
        List<Namespace> mockNamespaces = new ArrayList<>();
        
        // 创建一些常见的命名空间
        String[] namespaceNames = {
            "default", "kube-system", "kube-public", "kube-node-lease", 
            "app-dev", "app-test", "app-prod", "monitoring", "logging"
        };
        
        for (String namespaceName : namespaceNames) {
            // 手动创建Namespace对象
            Namespace namespace = new Namespace();
            
            // 创建元数据
            ObjectMeta metadata = new ObjectMeta();
            metadata.setName(namespaceName);
            metadata.setCreationTimestamp(Instant.now().toString());
            
            // 添加一些标签和注释
            Map<String, String> labels = new HashMap<>();
            labels.put("environment", namespaceName.contains("dev") ? "development" : 
                                     namespaceName.contains("test") ? "testing" : 
                                     namespaceName.contains("prod") ? "production" : "system");
            metadata.setLabels(labels);
            
            Map<String, String> annotations = new HashMap<>();
            annotations.put("description", "Mock namespace for " + namespaceName);
            metadata.setAnnotations(annotations);
            
            // 创建状态
            NamespaceStatus status = new NamespaceStatus();
            status.setPhase("Active");
            
            // 设置命名空间的元数据和状态
            namespace.setMetadata(metadata);
            namespace.setStatus(status);
            
            mockNamespaces.add(namespace);
        }
        
        return mockNamespaces;
    }

    protected RichNamespaceInfo convertToRichNamespaceInfo(String env, Namespace ns) {
        return new RichNamespaceInfo(
                env,
                ns.getMetadata().getName(),
                Instant.parse(ns.getMetadata().getCreationTimestamp()),
                ns.getStatus().getPhase(),
                ns.getMetadata().getLabels(),
                ns.getMetadata().getAnnotations()
        );
    }
}

package com.joe.task.controller;

import com.google.common.collect.Lists;
import com.joe.task.config.KubernetesClientManager;
import com.joe.task.dto.*;
import com.joe.task.entity.PageBean;
import com.joe.task.entity.Result;
import com.joe.task.service.k8s.*;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/kubernetes")
public class KubernetesController {

    private static final Logger log = LoggerFactory.getLogger(KubernetesController.class);

    @Autowired
    private NamespaceService namespaceService;

    @Autowired
    private PodService podService;

    @Autowired
    private LogService logService;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private ConfigMapService configMapService;

    @Autowired
    private SecretService secretService;

    @Autowired
    private EventService eventService;

    @PostMapping("/namespaces")
    public Result getNamespaces(@RequestParam(required = false) String env)
    {
        try
        {
            if (StringUtils.isBlank(env))
            {
                return Result.ok(new PageBean<>(Lists.newArrayList(), 0L));
            }
            
            List<NamespaceInfo> namespaces = podService.getAllNamespaces(env);

            return Result.ok(new PageBean<>(namespaces, (long) namespaces.size()));
        }
        catch (Exception e)
        {
            return Result.error("获取命名空间列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取命名空间完整列表
     * @param env 环境名称
     * @param name 命名空间名称过滤条件（可选）
     * @param status 命名空间状态过滤条件（可选）
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @return 命名空间列表结果
     */
    @PostMapping("/namespaces_full")
    public Result getNamespacesFullList(
            @RequestParam String env,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        log.info("获取环境 {} 的完整命名空间列表，过滤名称: {}, 状态: {}, 页码: {}, 每页大小: {}", 
                env, name, status, pageNo, pageSize);
        
        try {
            if (StringUtils.isBlank(env)) {
                log.warn("环境参数为空");
                return Result.error("环境参数不能为空");
            }

            // 获取命名空间详情
            List<RichNamespaceInfo> richNamespaceInfoList = namespaceService.getAllNamespaceDetails(env, name).stream()
                    .filter(ns -> StringUtils.isBlank(status) || ns.getStatus().equalsIgnoreCase(status))
                    .map(ns -> {
                        ns.setEnv(env);
                        return ns;
                    })
                    .collect(Collectors.toList());
            
            log.info("成功获取命名空间详情，共 {} 个命名空间", richNamespaceInfoList.size());

            // 实现分页逻辑
            int total = richNamespaceInfoList.size();
            int fromIndex = (pageNo - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            
            List<RichNamespaceInfo> pagedList;
            if (total > 0 && fromIndex < total) {
                pagedList = richNamespaceInfoList.subList(fromIndex, toIndex);
            } else {
                pagedList = Lists.newArrayList();
            }
            
            log.info("分页后返回 {} 个命名空间，总数: {}", pagedList.size(), total);
            return Result.ok(new PageBean<>(pagedList, (long) total));
        } catch (IllegalArgumentException e) {
            log.error("获取命名空间列表参数错误", e);
            return Result.error("参数错误: " + e.getMessage());
        } catch (IllegalStateException e) {
            log.error("获取命名空间列表状态错误", e);
            return Result.error("系统状态错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("获取命名空间列表失败", e);
            return Result.error("获取命名空间列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/namespaces/list")
    public Result getNamespacesList(
        @RequestParam String env,
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") Integer pageNo,
        @RequestParam(defaultValue = "10") Integer pageSize) 
    {
        try {
            if (StringUtils.isBlank(env)) {
                return Result.ok(new PageBean<>(Lists.newArrayList(), 0L));
            }
            
            List<NamespaceInfo> namespaces = podService.getAllNamespaces(env)
                .stream()
                .filter(ns -> StringUtils.isBlank(name) || 
                        StringUtils.containsIgnoreCase(ns.getName(), name))
                .collect(Collectors.toList());

            // 添加状态过滤逻辑
            List<NamespaceInfo> filtered = namespaces.stream()
                .filter(ns -> StringUtils.isBlank(status) || ns.getStatus().equalsIgnoreCase(status))
                .collect(Collectors.toList());
            
            // 实现分页逻辑
            int total = filtered.size();
            int fromIndex = (pageNo - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            List<NamespaceInfo> pagedList = filtered.subList(fromIndex, toIndex);
            
            return Result.ok(new PageBean<>(pagedList, (long) total));
        } catch (Exception e) {
            log.error("Failed to get namespaces list", e);
            return Result.error("获取命名空间列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/pods")
    public Result getPods(@RequestParam String env, @RequestParam String namespace, @RequestParam String status, @RequestParam String name)
    {
        try
        {
            List<PodInfo> pods = podService.getPodsInNamespace(env, namespace,
                    StringUtils.isBlank(status)?Optional.empty():Optional.of(status),
                    StringUtils.isBlank(name)?Optional.empty():Optional.of(name)
                    );
            return Result.ok(new PageBean<>(pods, (long) pods.size()));
        }
        catch (Exception e)
        {
            return Result.error("获取Pod列表失败: " + e.getMessage());
        }
    }


    @PostMapping("/logs")
    public Result getLogs(@RequestParam String env, 
                         @RequestParam String namespace, 
                         @RequestParam String podName) {
        try {
            String logs = logService.getPodsLogs(
                env,
                namespace,
                Optional.of(podName),
                Optional.empty(),
                Optional.empty()
            );
            
            // 在Java控制台打印日志
            System.out.println("=================== Pod Logs Start ===================");
            System.out.println("Pod: " + podName);
            System.out.println("Namespace: " + namespace);
            System.out.println("Environment: " + env);
            System.out.println("---------------------------------------------------");
            System.out.println(logs);
            System.out.println("=================== Pod Logs End ===================");
            
            return Result.ok("日志已在服务器控制台打印");
        } catch (Exception e) {
            return Result.error("获取Pod日志失败: " + e.getMessage());
        }
    }

    @GetMapping("/logs/download")
    public void downloadLogs(@RequestParam String env,
                            @RequestParam String namespace,
                            @RequestParam String podName,
                            HttpServletResponse response) throws IOException {
        
        log.info("Starting log download for pod: {} in namespace: {}", podName, namespace);
        
        // 修改响应头设置
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", 
            "attachment; filename=" + env + "-" + namespace + "-" + podName + "-logs.zip");
        
        // 移除 chunked encoding
        response.setHeader("Transfer-Encoding", null);
        
        try {
            logService.streamPodLogsAsZip(
                env,
                namespace,
                podName,
                response.getOutputStream()
            );
            
            response.flushBuffer();
            
        } catch (Exception e) {
            log.error("Error downloading logs for pod: {}", podName, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error downloading logs: " + e.getMessage());
        }
    }

    @PostMapping("/deployments")
    public Result getDeployments(@RequestParam String env, 
                                 @RequestParam String namespace,
                                 @RequestParam(required = false) String name) {
        try {
            List<DeploymentInfo> deployments = deploymentService.getDeploymentsInNamespace(env, namespace, Optional.ofNullable(name));
            return Result.ok(new PageBean<>(deployments, (long) deployments.size()));
        } catch (Exception e) {
            return Result.error("获取Deployment列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/configmaps")
    public Result getConfigMaps(@RequestParam String env,
                              @RequestParam String namespace,
                              @RequestParam(required = false) String name) {
        try {
            List<ConfigMapInfo> configMaps = configMapService.getConfigMapsInNamespace(env, namespace, Optional.ofNullable(name));
            return Result.ok(new PageBean<>(configMaps, (long) configMaps.size()));
        } catch (Exception e) {
            log.error("Failed to get ConfigMaps", e);
            return Result.error("Failed to get ConfigMaps: " + e.getMessage());
        }
    }

    /**
     * Update ConfigMap data
     */
    @PostMapping("/configmaps/update")
    public Result updateConfigMap(@RequestBody ConfigMapUpdateRequest request) {
        try {
            boolean success = configMapService.updateConfigMap(
                request.getEnv(),
                request.getNamespace(),
                request.getName(),
                request.getData()
            );
            
            if (success) {
                return Result.ok("ConfigMap updated successfully");
            } else {
                return Result.error("Failed to update ConfigMap");
            }
        } catch (Exception e) {
            log.error("Error updating ConfigMap", e);
            return Result.error("Error updating ConfigMap: " + e.getMessage());
        }
    }

    @PostMapping("/secrets")
    public Result getSecrets(@RequestParam String env, 
                           @RequestParam String namespace,
                           @RequestParam(required = false) String name) {
        try {
            List<SecretInfo> secrets = secretService.getSecretsInNamespace(
                env, 
                namespace, 
                Optional.ofNullable(name)
            );
            return Result.ok(new PageBean<>(secrets, (long) secrets.size()));
        } catch (Exception e) {
            return Result.error("获取Secret列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/events")
    public Result getEvents(@RequestParam String env, 
                           @RequestParam String namespace,
                           @RequestParam(required = false) String type,
                           @RequestParam(defaultValue = "1") Integer pageNo,
                           @RequestParam(defaultValue = "10") Integer pageSize)
    {
        try {
            if (StringUtils.isBlank(namespace)) {
                return Result.error("命名空间不能为空");
            }

            log.info("Fetching events for env: {}, namespace: {}, type: {}, page: {}, size: {}", 
                env, namespace, type, pageNo, pageSize);
            
            PageBean<EventInfo> events = eventService.getEventsInNamespace(
                env, 
                namespace, 
                StringUtils.isBlank(type) ? Optional.empty() : Optional.of(type),
                pageNo,
                pageSize
            );
            
            log.info("Found {} events in {}:{}", events.getTotalCount(), env, namespace);
            return Result.ok(events);
        } catch (Exception e) {
            log.error("Error fetching events", e);
            return Result.error("获取Event列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/logs/init")
    public Result getInitContainerLogs(@RequestParam String env, 
                                     @RequestParam String namespace, 
                                     @RequestParam String podName) {
        try {
            String logs = logService.getInitContainerLogs(env, namespace, podName);
            return Result.ok(logs);
        } catch (Exception e) {
            return Result.error("获取Init Container日志失败: " + e.getMessage());
        }
    }

    @PostMapping("/logs/events")
    public Result getPodEvents(@RequestParam String env, 
                             @RequestParam String namespace, 
                             @RequestParam String podName) {
        try {
            String events = logService.getPodEvents(env, namespace, podName);
            return Result.ok(events);
        } catch (Exception e) {
            return Result.error("获取Pod事件失败: " + e.getMessage());
        }
    }

    @PostMapping("/logs/describe")
    public Result getPodDescription(@RequestParam String env, 
                                  @RequestParam String namespace, 
                                  @RequestParam String podName) {
        try {
            String description = logService.getPodDescription(env, namespace, podName);
            return Result.ok(description);
        } catch (Exception e) {
            return Result.error("获取Pod描述失败: " + e.getMessage());
        }
    }

    @PostMapping("/logs/all")
    public Result getAllPodInfo(@RequestParam String env, 
                              @RequestParam String namespace, 
                              @RequestParam String podName) {
        try {
            String allInfo = logService.getAllPodInfo(env, namespace, podName);
            return Result.ok(allInfo);
        } catch (Exception e) {
            return Result.error("获取Pod所有信息失败: " + e.getMessage());
        }
    }

    /**
     * Delete a pod in the specified namespace
     */
    @PostMapping("/pods/delete")
    public Result deletePod(@RequestParam String env,
                          @RequestParam String namespace,
                          @RequestParam String podName) {
        log.info("Deleting pod: {} in namespace: {} for environment: {}", podName, namespace, env);
        
        try {
            if (StringUtils.isBlank(env)) {
                return Result.error("环境参数不能为空");
            }
            if (StringUtils.isBlank(namespace)) {
                return Result.error("命名空间不能为空");
            }
            if (StringUtils.isBlank(podName)) {
                return Result.error("Pod名称不能为空");
            }

            podService.deletePod(env, namespace, podName);
            return Result.ok("Pod删除成功");
        } catch (Exception e) {
            log.error("Failed to delete pod: {} in namespace: {}", podName, namespace, e);
            return Result.error("删除Pod失败: " + e.getMessage());
        }
    }
} 
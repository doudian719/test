package com.joe.task.controller;

import com.joe.task.dto.knative.KnativeServiceInfo;
import com.joe.task.entity.PageBean;
import com.joe.task.entity.Result;
import com.joe.task.service.knative.KnativeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/api/knative")
public class KnativeController {
    
    @Autowired
    private KnativeService knativeService;
    
    @PostMapping("/service/list")
    public Result getKnativeServices(
            @RequestParam String env,
            @RequestParam String namespace,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        try {
            List<KnativeServiceInfo> services = knativeService.getKnativeServicesInNamespace(env, namespace);
            
            // 应用过滤条件
            if (StringUtils.isNotBlank(name) || StringUtils.isNotBlank(status)) {
                services = services.stream()
                    .filter(svc -> {
                        boolean nameMatch = StringUtils.isBlank(name) || 
                                         StringUtils.containsIgnoreCase(svc.getName(), name);
                        
                        boolean statusMatch = StringUtils.isBlank(status) ||
                                            svc.getConditions().stream()
                                               .anyMatch(c -> "Ready".equals(c.getType()) && 
                                                            status.equals(c.getStatus()));
                        
                        return nameMatch && statusMatch;
                    })
                    .collect(Collectors.toList());
            }
            
            // 计算分页
            int start = (page - 1) * size;
            int end = Math.min(start + size, services.size());
            List<KnativeServiceInfo> pagedServices = services.subList(start, end);
            
            return Result.ok(new PageBean<>(pagedServices, (long) services.size()));
        } catch (Exception e) {
            log.error("Failed to get Knative services", e);
            return Result.error("获取Knative服务列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/service/yaml")
    public Result getKnativeServiceYaml(
            @RequestParam String env,
            @RequestParam String namespace,
            @RequestParam String name) {
        try {
            String yaml = knativeService.getKnativeServiceYaml(env, namespace, name);
            return Result.ok(yaml);
        } catch (Exception e) {
            log.error("Failed to get Knative service YAML", e);
            return Result.error("获取Knative服务YAML失败: " + e.getMessage());
        }
    }

    @PostMapping("/service/delete")
    public Result deleteKnativeService(
            @RequestParam String env,
            @RequestParam String namespace,
            @RequestParam String name) {
        try {
            knativeService.deleteKnativeService(env, namespace, name);
            return Result.ok("Successfully deleted Knative service");
        } catch (Exception e) {
            log.error("Failed to delete Knative service", e);
            return Result.error("删除Knative服务失败: " + e.getMessage());
        }
    }

    /**
     * Create a new revision for an existing Knative service
     *
     * @param env The environment (cluster) where the service is deployed
     * @param namespace The namespace where the service is deployed
     * @param name The name of the service
     * @return Response containing the new revision name
     */
    @PostMapping("/service/update")
    public ResponseEntity<Map<String, Object>> createNewRevision(
            @RequestParam String env,
            @RequestParam String namespace,
            @RequestParam String name) {
        try {
            String newRevision = knativeService.createNewRevision(env, namespace, name);
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("message", "Successfully created new revision");
            
            Map<String, Object> data = new HashMap<>();
            data.put("newRevision", newRevision);
            response.put("data", data);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create new revision for service {}/{} in environment: {}", namespace, name, env, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 1);
            response.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

}

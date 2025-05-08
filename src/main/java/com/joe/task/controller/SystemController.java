package com.joe.task.controller;

import com.joe.task.entity.EnvConfig;
import com.joe.task.entity.ResourceType;
import com.joe.task.entity.Result;
import com.joe.task.service.EnvConfigService;
import com.joe.task.service.ResourceTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/system")
public class SystemController {

    private final EnvConfigService envConfigService;
    private final ResourceTypeService resourceTypeService;

    @Autowired
    public SystemController(EnvConfigService envConfigService, ResourceTypeService resourceTypeService) {
        this.envConfigService = envConfigService;
        this.resourceTypeService = resourceTypeService;
    }

    /**
     * ENV page
     */
    @GetMapping("/env.html")
    public String env() {
        return "system/env";
    }

    /**
     * Get all environments
     */
    @GetMapping("/env/list")
    @ResponseBody
    public Result getAllEnvs() {
        List<EnvConfig> envs = envConfigService.getAllEnvs();
        List<Map<String, Object>> envList = envs.stream().map(env -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", env.getId());
            map.put("name", env.getName());
            map.put("serverURL", env.getServerUrl());
            map.put("token", env.getToken());
            map.put("sequence", env.getSequence());
            map.put("isHidden", env.getIsHidden());
            if (env.getResource() != null) {
                map.put("resourceTypeId", env.getResource().getId());
                map.put("resourceTypeName", env.getResource().getResourceName());
            } else {
                map.put("resourceTypeId", null);
                map.put("resourceTypeName", "");
            }
            return map;
        }).collect(Collectors.toList());

        Result result = Result.ok();
        result.put("data", envList);
        result.put("success", true);
        result.put("msg", "Success");
        return result;
    }

    /**
     * Get all visible environments
     */
    @GetMapping("/env/visible")
    @ResponseBody
    public Result getAllVisibleEnvs(@RequestParam(value = "resourceType", required = false) String resourceType) {
        List<EnvConfig> envs;
        if (resourceType != null && !resourceType.isEmpty()) {
            // 根据 resourceType 过滤
            envs = envConfigService.getAllVisibleEnvsByResourceType(resourceType);
        } else {
            envs = envConfigService.getAllVisibleEnvs();
        }
        Result result = Result.ok();
        result.put("data", envs);
        result.put("success", true);
        result.put("msg", "Success");
        return result;
    }

    /**
     * Get environment by id
     */
    @GetMapping("/env/{id}")
    @ResponseBody
    public Result getEnvById(@PathVariable Long id) {
        Optional<EnvConfig> env = envConfigService.getEnvById(id);
        if (env.isPresent()) {
            Result result = Result.ok();
            result.put("data", env.get());
            result.put("success", true);
            result.put("msg", "Success");
            return result;
        } else {
            Result result = Result.error("Environment not found");
            result.put("success", false);
            return result;
        }
    }

    /**
     * Save environment
     */
    @PostMapping("/env/save")
    @ResponseBody
    public Result saveEnv(@RequestBody Map<String, Object> envMap) {
        EnvConfig envConfig;
        if (envMap.get("id") != null) {
            // 编辑时先查出原有对象
            envConfig = envConfigService.getEnvById(Long.valueOf(envMap.get("id").toString())).orElse(new EnvConfig());
        } else {
            envConfig = new EnvConfig();
        }
        envConfig.setName((String) envMap.get("name"));
        envConfig.setServerUrl((String) envMap.get("serverURL"));
        envConfig.setToken((String) envMap.get("token"));
        envConfig.setSequence(envMap.get("sequence") != null ? Integer.valueOf(envMap.get("sequence").toString()) : 1);
        envConfig.setIsHidden(envMap.get("isHidden") != null ? Boolean.valueOf(envMap.get("isHidden").toString()) : false);

        // 处理 resourceTypeId
        Object resourceTypeIdObj = envMap.get("resourceTypeId");
        if (resourceTypeIdObj != null) {
            Long resourceTypeId = Long.valueOf(resourceTypeIdObj.toString());
            ResourceType resourceType = resourceTypeService.getResourceTypeById(resourceTypeId).orElse(null);
            envConfig.setResource(resourceType);
        } else {
            envConfig.setResource(null);
        }

        EnvConfig saved = envConfigService.saveEnv(envConfig);
        Result result = Result.ok();
        result.put("data", saved);
        result.put("success", true);
        result.put("msg", "Environment saved successfully");
        return result;
    }

    /**
     * Delete environment
     */
    @DeleteMapping("/env/{id}")
    @ResponseBody
    public Result deleteEnv(@PathVariable Long id) {
        envConfigService.deleteEnv(id);
        Result result = Result.ok();
        result.put("success", true);
        result.put("msg", "Environment deleted successfully");
        return result;
    }

    /**
     * Resource Type page
     */
    @GetMapping("/resource-type.html")
    public String resourceType() {
        return "system/resource-type";
    }

    /**
     * Get all resource types
     */
    @GetMapping("/resource-type/list")
    @ResponseBody
    public Result getAllResourceTypes() {
        List<ResourceType> types = resourceTypeService.getAllResourceTypes();
        Result result = Result.ok();
        result.put("data", types);
        result.put("success", true);
        result.put("msg", "Success");
        return result;
    }

    /**
     * Get resource type by id
     */
    @GetMapping("/resource-type/{id}")
    @ResponseBody
    public Result getResourceTypeById(@PathVariable Long id) {
        Optional<ResourceType> type = resourceTypeService.getResourceTypeById(id);
        if (type.isPresent()) {
            Result result = Result.ok();
            result.put("data", type.get());
            result.put("success", true);
            result.put("msg", "Success");
            return result;
        } else {
            Result result = Result.error("Resource type not found");
            result.put("success", false);
            return result;
        }
    }

    /**
     * Save resource type
     */
    @PostMapping("/resource-type/save")
    @ResponseBody
    public Result saveResourceType(@RequestBody ResourceType resourceType) {
        ResourceType saved = resourceTypeService.saveResourceType(resourceType);
        Result result = Result.ok();
        result.put("data", saved);
        result.put("success", true);
        result.put("msg", "Resource type saved successfully");
        return result;
    }

    /**
     * Delete resource type
     */
    @DeleteMapping("/resource-type/{id}")
    @ResponseBody
    public Result deleteResourceType(@PathVariable Long id) {
        resourceTypeService.deleteResourceType(id);
        Result result = Result.ok();
        result.put("success", true);
        result.put("msg", "Resource type deleted successfully");
        return result;
    }
} 
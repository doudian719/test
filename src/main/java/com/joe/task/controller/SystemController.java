package com.joe.task.controller;

import com.joe.task.entity.EnvConfig;
import com.joe.task.entity.Result;
import com.joe.task.service.EnvConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class SystemController {

    private final EnvConfigService envConfigService;

    @Autowired
    public SystemController(EnvConfigService envConfigService) {
        this.envConfigService = envConfigService;
    }

    /**
     * ENV page
     */
    @GetMapping("/system/env.html")
    public String env() {
        return "system/env";
    }

    /**
     * Get all environments
     */
    @GetMapping("/system/env/list")
    @ResponseBody
    public Result getAllEnvs() {
        List<EnvConfig> envs = envConfigService.getAllEnvs();
        Result result = Result.ok();
        result.put("data", envs);
        result.put("success", true);
        result.put("msg", "Success");
        return result;
    }

    /**
     * Get all visible environments
     */
    @GetMapping("/system/env/visible")
    @ResponseBody
    public Result getAllVisibleEnvs() {
        List<EnvConfig> envs = envConfigService.getAllVisibleEnvs();
        Result result = Result.ok();
        result.put("data", envs);
        result.put("success", true);
        result.put("msg", "Success");
        return result;
    }

    /**
     * Get environment by id
     */
    @GetMapping("/system/env/{id}")
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
    @PostMapping("/system/env/save")
    @ResponseBody
    public Result saveEnv(@RequestBody EnvConfig envConfig) {
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
    @DeleteMapping("/system/env/{id}")
    @ResponseBody
    public Result deleteEnv(@PathVariable Long id) {
        envConfigService.deleteEnv(id);
        Result result = Result.ok();
        result.put("success", true);
        result.put("msg", "Environment deleted successfully");
        return result;
    }

    /**
     * Get environment options for dropdown
     * This API is used by other pages to get environment options
     */
    @GetMapping("/system/env/options")
    @ResponseBody
    public Result getEnvOptions() {
        List<EnvConfig> envs = envConfigService.getAllVisibleEnvs();
        Result result = Result.ok();
        
        // 转换为选项格式
        List<Object> options = envs.stream()
                .map(env -> {
                    Result option = Result.ok();
                    option.put("value", env.getName());
                    option.put("label", env.getName());
                    option.put("k8sServerHost", env.getK8sServerHost());
                    option.put("k8sToken", env.getK8sToken());
                    return option;
                })
                .collect(Collectors.toList());
        
        result.put("data", options);
        result.put("success", true);
        result.put("msg", "Success");
        return result;
    }
} 
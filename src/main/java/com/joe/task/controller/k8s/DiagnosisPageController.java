package com.joe.task.controller.k8s;

import com.joe.task.config.KubernetesClientManager;
import com.joe.task.service.EnvConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.stream.Collectors;

@Controller
public class DiagnosisPageController {

    private final KubernetesClientManager kubernetesClientManager;
    private final EnvConfigService envConfigService;

    public DiagnosisPageController(KubernetesClientManager kubernetesClientManager,
                               EnvConfigService envConfigService) {
        this.kubernetesClientManager = kubernetesClientManager;
        this.envConfigService = envConfigService;
    }

    @GetMapping("/kubernetes/diagnosis")
    public String diagnosis(Model model, 
                          @RequestParam(required = false) String env,
                          @RequestParam(required = false) String namespace,
                          @RequestParam(required = false) String podName) {
        model.addAttribute("environments", 
            envConfigService.getAllVisibleEnvs().stream()
                .map(e -> e.getName())
                .collect(Collectors.toList()));
        model.addAttribute("env", env);
        model.addAttribute("namespace", namespace);
        model.addAttribute("podName", podName);
        return "kubernetes/diagnosis";
    }
} 
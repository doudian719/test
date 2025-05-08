package com.joe.task.controller.k8s;

import com.joe.task.service.k8s.NamespaceService;
import com.joe.task.service.k8s.PodService;
import com.joe.task.config.KubernetesClientManager;
import com.joe.task.service.EnvConfigService;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Container;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;
import io.fabric8.kubernetes.client.KubernetesClient;

@RestController
@RequestMapping("/api/k8s")
public class K8sLogController {

    @Autowired
    private KubernetesClientManager clientManager;
    @Autowired
    private NamespaceService namespaceService;
    @Autowired
    private PodService podService;
    @Autowired
    private EnvConfigService envConfigService;

    // 获取环境列表
    @GetMapping("/environments")
    public List<String> getEnvironments() {
        // 返回所有可用环境名
        return envConfigService.getAllVisibleEnvsByResourceType("K8S").stream().map(e -> e.getName()).collect(Collectors.toList());
    }

    // 获取namespace列表
    @GetMapping("/namespaces")
    public List<String> getNamespaces(@RequestParam String env) {
        return namespaceService.getNamespaces(env).list().getItems().stream().map(ns -> ns.getMetadata().getName()).collect(Collectors.toList());
    }

    // 获取pod列表
    @GetMapping("/pods")
    public List<String> getPods(@RequestParam String env, @RequestParam String namespace) {
        return podService.getPodsInNamespace(env, namespace, Optional.empty(), Optional.empty()).stream().map(pod -> pod.getName()).collect(Collectors.toList());
    }

    // 获取container列表
    @GetMapping("/containers")
    public List<String> getContainers(@RequestParam String env, @RequestParam String namespace, @RequestParam String pod) {
        KubernetesClient client = clientManager.getClient(env);
        Pod podObj = client.pods().inNamespace(namespace).withName(pod).get();
        if (podObj == null) return List.of();
        return podObj.getSpec().getContainers().stream().map(Container::getName).collect(Collectors.toList());
    }
} 
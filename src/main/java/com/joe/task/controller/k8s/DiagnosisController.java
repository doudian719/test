package com.joe.task.controller.k8s;

import com.joe.task.service.k8s.DiagnosisService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/k8s/diagnosis")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    @GetMapping("/pod")
    public Map<String, Object> diagnosePod(
            @RequestParam String env,
            @RequestParam String namespace,
            @RequestParam String podName) {
        return diagnosisService.diagnosePod(env, namespace, podName);
    }
} 
package com.joe.task.controller.crd;

import com.joe.task.model.crd.ClusterFlowParameters;
import com.joe.task.model.crd.ClusterOutputParameters;
import com.joe.task.service.crd.CRDTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/crd-templates")
@Slf4j
public class CRDTemplateController {
    private final CRDTemplateService crdTemplateService;

    public CRDTemplateController(CRDTemplateService crdTemplateService) {
        this.crdTemplateService = crdTemplateService;
    }

    @PostMapping("/clusterflow")
    public ResponseEntity<Map<String, Object>> createClusterFlow(
            @RequestParam String env,
            @RequestParam String namespace,
            @RequestBody ClusterFlowParameters parameters) {
        try {
            Map<String, Object> result = crdTemplateService.createClusterFlow(env, namespace, parameters);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to create ClusterFlow", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/clusteroutput")
    public ResponseEntity<Map<String, Object>> createClusterOutput(
            @RequestParam String env,
            @RequestParam String namespace,
            @RequestBody ClusterOutputParameters parameters) {
        try {
            Map<String, Object> result = crdTemplateService.createClusterOutput(env, namespace, parameters);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to create ClusterOutput", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
} 
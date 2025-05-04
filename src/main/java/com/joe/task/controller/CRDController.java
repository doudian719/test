package com.joe.task.controller;

import com.joe.task.service.k8s.CRDService;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/kubernetes/crd")
public class CRDController {

    private final CRDService crdService;

    @Autowired
    public CRDController(CRDService crdService) {
        this.crdService = crdService;
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<?> getCRDs(@RequestParam String env,
                                     @RequestParam(required = false) String namespace,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int pageSize) {
        try {
            List<CustomResourceDefinition> crds = crdService.getCRDs(env);
            // 关键字过滤
            if (keyword != null && !keyword.trim().isEmpty()) {
                String lower = keyword.trim().toLowerCase();
                crds = crds.stream().filter(
                    crd -> crd.getMetadata().getName().toLowerCase().contains(lower)
                ).collect(java.util.stream.Collectors.toList());
            }
            // 过滤scope
            if (namespace == null || namespace.trim().isEmpty()) {
                crds = crds.stream().filter(
                    crd -> {
                        String scope = crd.getSpec().getScope();
                        return "Namespaced".equalsIgnoreCase(scope) || "Cluster".equalsIgnoreCase(scope);
                    }
                ).collect(java.util.stream.Collectors.toList());
            } else {
                crds = crds.stream().filter(
                    crd -> "Namespaced".equalsIgnoreCase(crd.getSpec().getScope())
                ).collect(java.util.stream.Collectors.toList());
            }
            int total = crds.size();
            int from = Math.max((page - 1) * pageSize, 0);
            int to = Math.min(from + pageSize, total);
            List<CustomResourceDefinition> pageList = from < to ? crds.subList(from, to) : java.util.Collections.emptyList();
            java.util.Map<String, Object> msg = new java.util.HashMap<>();
            msg.put("pageData", pageList);
            msg.put("pageNo", page);
            msg.put("pageSize", pageSize);
            msg.put("totalCount", total);
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("msg", msg);
            result.put("code", 0);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("msg", e.getMessage());
            result.put("code", 1);
            return ResponseEntity.badRequest().body(result);
        }
    }

    @GetMapping("/{crdName}")
    @ResponseBody
    public ResponseEntity<?> getCRD(@RequestParam String env, @PathVariable String crdName) {
        try {
            CustomResourceDefinition crd = crdService.getCRD(env, crdName);
            return ResponseEntity.ok(crd);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<?> createCRD(@RequestParam String env, @RequestBody CustomResourceDefinition crd) {
        try {
            CustomResourceDefinition createdCRD = crdService.createCRD(env, crd);
            return ResponseEntity.ok(createdCRD);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{crdName}")
    @ResponseBody
    public ResponseEntity<?> updateCRD(@RequestParam String env, @PathVariable String crdName, 
            @RequestBody CustomResourceDefinition crd) {
        try {
            CustomResourceDefinition updatedCRD = crdService.updateCRD(env, crd);
            return ResponseEntity.ok(updatedCRD);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{crdName}")
    @ResponseBody
    public ResponseEntity<?> deleteCRD(@RequestParam String env, @PathVariable String crdName) {
        try {
            crdService.deleteCRD(env, crdName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{crdName}/instances")
    @ResponseBody
    public ResponseEntity<?> getCRDInstances(@RequestParam String env, @PathVariable String crdName,
            @RequestParam(required = false) String namespace) {
        try {
            List<Map<String, Object>> instances = crdService.getCRDInstances(env, crdName, namespace);
            return ResponseEntity.ok(instances);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{crdName}/instances/{instanceName}")
    @ResponseBody
    public ResponseEntity<?> getCRDInstance(@RequestParam String env, @PathVariable String crdName,
            @RequestParam String namespace, @PathVariable String instanceName) {
        try {
            Map<String, Object> instance = crdService.getCRDInstance(env, crdName, namespace, instanceName);
            return ResponseEntity.ok(instance);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{crdName}/instances")
    @ResponseBody
    public ResponseEntity<?> createCRDInstance(
        @RequestBody Map<String, Object> body,
        @PathVariable String crdName
    ) {
        try {
            String env = (String) body.get("env");
            String namespace = (String) body.get("namespace");
            Map<String, Object> instance = (Map<String, Object>) body.get("instance");
            Map<String, Object> createdInstance = crdService.createCRDInstance(env, crdName, namespace, instance);
            return ResponseEntity.ok(createdInstance);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{crdName}/instances/{instanceName}")
    @ResponseBody
    public ResponseEntity<?> updateCRDInstance(
        @RequestBody Map<String, Object> body,
        @PathVariable String crdName,
        @PathVariable String instanceName
    ) {
        try {
            String env = (String) body.get("env");
            String namespace = (String) body.get("namespace");
            Map<String, Object> instance = (Map<String, Object>) body.get("instance");
            Map<String, Object> updatedInstance = crdService.updateCRDInstance(env, crdName, namespace, instanceName, instance);
            return ResponseEntity.ok(updatedInstance);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{crdName}/instances/{instanceName}")
    @ResponseBody
    public ResponseEntity<?> deleteCRDInstance(@RequestParam String env, @PathVariable String crdName,
            @RequestParam String namespace, @PathVariable String instanceName) {
        try {
            crdService.deleteCRDInstance(env, crdName, namespace, instanceName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/namespaced-list")
    @ResponseBody
    public ResponseEntity<?> getNamespacedCRDs(@RequestParam String env) {
        List<CustomResourceDefinition> crds = crdService.getCRDs(env);
        List<CustomResourceDefinition> namespacedCrds = crds.stream()
            .filter(crd -> "Namespaced".equalsIgnoreCase(crd.getSpec().getScope()))
            .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(namespacedCrds);
    }

    @GetMapping("/instances")
    @ResponseBody
    public ResponseEntity<?> getCRDInstances(
        @RequestParam String env,
        @RequestParam String crdName,
        @RequestParam String namespace,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int pageSize
    ) {
        List<Map<String, Object>> all = crdService.getCRDInstances(env, crdName, namespace);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lower = keyword.trim().toLowerCase();
            all = all.stream().filter(
                inst -> {
                    Object name = ((Map)inst.get("metadata")).get("name");
                    return name != null && name.toString().toLowerCase().contains(lower);
                }
            ).collect(java.util.stream.Collectors.toList());
        }
        int total = all.size();
        int from = Math.max((page - 1) * pageSize, 0);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> pageList = from < to ? all.subList(from, to) : java.util.Collections.emptyList();
        java.util.Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("pageData", pageList);
        msg.put("pageNo", page);
        msg.put("pageSize", pageSize);
        msg.put("totalCount", total);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("msg", msg);
        result.put("code", 0);
        return ResponseEntity.ok(result);
    }
} 
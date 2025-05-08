package com.joe.task.controller;

import com.joe.task.entity.EnvConfig;
import com.joe.task.entity.Result;
import com.joe.task.service.EnvConfigService;
import com.joe.task.service.HasuraService;
import com.joe.task.service.HasuraService.RemoteSchema;
import com.joe.task.service.HasuraService.SchemaHealthStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/hasura")
@Slf4j
public class HasuraController {

    @Autowired
    private EnvConfigService envConfigService;

    @Autowired
    private HasuraService hasuraService;

    @Data
    public static class AddSchemaRequest {
        private String env;
        private String namespace;
        private String serviceName;
    }

    @Data
    public static class DeleteSchemaRequest {
        private String env;
        private String schemaName;
    }

    @Data
    public static class DeleteSchemaByNamespaceRequest {
        private String env;
        private String namespace;
        private String serviceName;
    }

    @Data
    public static class SchemaHealthResponse {
        private boolean healthy;
        private String details;

        public SchemaHealthResponse(boolean healthy, String details) {
            this.healthy = healthy;
            this.details = details;
        }
    }

    @Data
    public static class RefreshSchemaRequest {
        private String env;
        private String schemaName;
    }

    @PostMapping("/schema/add")
    public Result addRemoteSchema(@RequestBody AddSchemaRequest request) {
        log.info("Adding remote schema for env: {}, namespace: {}, service: {}", 
                 request.getEnv(), request.getNamespace(), request.getServiceName());
        
        try {
            boolean success = hasuraService.addRemoteSchema(
                request.getEnv(), 
                request.getNamespace(), 
                request.getServiceName()
            );
            
            if (success) {
                return Result.ok("Schema added successfully");
            } else {
                return Result.error("Failed to add schema");
            }
        } catch (Exception e) {
            log.error("Error adding remote schema", e);
            return Result.error("Failed to add schema: " + e.getMessage());
        }
    }

    @PostMapping("/schema/delete")
    public Result deleteRemoteSchema(@RequestBody DeleteSchemaRequest request) {
        log.info("Deleting remote schema: {} from env: {}", request.getSchemaName(), request.getEnv());
        
        try {
            boolean success = hasuraService.deleteRemoteSchema(request.getEnv(), request.getSchemaName());
            
            if (success) {
                return Result.ok("Schema deleted successfully");
            } else {
                return Result.error("Failed to delete schema");
            }
        } catch (Exception e) {
            log.error("Error deleting remote schema", e);
            return Result.error("Failed to delete schema: " + e.getMessage());
        }
    }

    @DeleteMapping("/schema/delete/by-namespace")
    public Result deleteRemoteSchemaByNamespace(@RequestBody DeleteSchemaByNamespaceRequest request) {
        log.info("Deleting remote schema for env: {}, namespace: {}, service: {}", 
                 request.getEnv(), request.getNamespace(), request.getServiceName());
        
        try {
            boolean success = hasuraService.deleteRemoteSchema(
                request.getEnv(), 
                request.getNamespace(), 
                request.getServiceName()
            );
            
            if (success) {
                return Result.ok("Schema deleted successfully");
            } else {
                return Result.error("Failed to delete schema");
            }
        } catch (Exception e) {
            log.error("Error deleting remote schema by namespace", e);
            return Result.error("Failed to delete schema: " + e.getMessage());
        }
    }

    @GetMapping("/schemas/search")
    public Result searchSchemas(@RequestParam String env, @RequestParam String keyword)
    {
        log.info("Searching schemas in env: {} with keyword: {}", env, keyword);

        // 如果是mock或test环境，返回模拟数据
        if ("mock".equalsIgnoreCase(env) || "test".equalsIgnoreCase(env)) {
            List<RemoteSchema> schemaList = new ArrayList<>();
            RemoteSchema schema1 = RemoteSchema.builder().name("user_service").url("http://mock-url/user").timeoutSeconds(30).forwardClientHeaders(true).comments("comment 1").build();
            RemoteSchema schema2 = RemoteSchema.builder().name("order_service").url("http://mock-url/order").timeoutSeconds(60).forwardClientHeaders(false).comments("comment 2").build();
            RemoteSchema schema3 = RemoteSchema.builder().name("inventory_service").url("http://mock-url/inventory").timeoutSeconds(30).forwardClientHeaders(true).comments("comment 3").build();

            SchemaHealthStatus healthStatus1 = SchemaHealthStatus.builder().name("user_service").healthy(true).details("ok").build();
            SchemaHealthStatus healthStatus2 = SchemaHealthStatus.builder().name("order_service").healthy(false).details("Schema error...").build();
            SchemaHealthStatus healthStatus3 = SchemaHealthStatus.builder().name("inventory_service").healthy(true).details("ok").build();

            schema1.setHealthStatus(healthStatus1);
            schema2.setHealthStatus(healthStatus2);
            schema3.setHealthStatus(healthStatus3);

            schemaList.add(schema1);
            schemaList.add(schema2);
            schemaList.add(schema3);
            return Result.ok(schemaList);
        }

        // 其他环境走主逻辑
        try {
            List<RemoteSchema> schemaList = hasuraService.listRemoteSchemas(env, keyword);

            return Result.ok(schemaList);
        } catch (Exception e) {
            log.error("Error searching schemas", e);
            return Result.error("Failed to search schemas: " + e.getMessage());
        }
    }

    @GetMapping("/schema/health/{env}/{schemaName}")
    public Result checkSchemaHealth(
            @PathVariable String env,
            @PathVariable String schemaName) {
        log.info("Checking health for schema: {} in env: {}", schemaName, env);
        
        try {
            List<SchemaHealthStatus> allStatuses = hasuraService.checkAllRemoteSchemasHealth(env);
            SchemaHealthStatus status = allStatuses.stream()
                .filter(s -> s.getName().equals(schemaName))
                .findFirst()
                .orElse(null);
            
            if (status == null) {
                return Result.error("Schema not found");
            }
            
            return Result.ok(new SchemaHealthResponse(
                status.isHealthy(),
                status.isHealthy() ? "Schema is healthy" : status.getDetails()
            ));
        } catch (Exception e) {
            log.error("Error checking schema health", e);
            return Result.error("Failed to check schema health: " + e.getMessage());
        }
    }

    @GetMapping("/schemas/health/{env}")
    public Result checkAllSchemasHealth(@PathVariable String env) {
        log.info("Checking health for all schemas in env: {}", env);
        
        try {
            List<SchemaHealthStatus> healthStatuses = hasuraService.checkAllRemoteSchemasHealth(env);
            return Result.ok(healthStatuses);
        } catch (Exception e) {
            log.error("Error checking all schemas health", e);
            return Result.error("Failed to check schemas health: " + e.getMessage());
        }
    }

    @GetMapping("/env/options")
    public Result getHasuraEnvOptions() {
        try {
            List<EnvConfig> envConfigList = envConfigService.getAllVisibleEnvsByResourceType("Hasura");

            // 从DB中获取所有环境
            List<Object> options = envConfigList.stream()
                    .map(envConfig -> {
                        Result option = Result.ok();
                        option.put("value", envConfig.getName());
                        option.put("label", envConfig.getName());
                        return option;
                    })
                    .collect(Collectors.toList());
            Result result = Result.ok();
            result.put("data", options);
            result.put("success", true);
            result.put("msg", "Success");
            return result;
        } catch (Exception e) {
            log.error("Failed to get hasura env options", e);
            return Result.error("Failed to get hasura env options: " + e.getMessage());
        }
    }

    @PostMapping("/schema/refresh")
    public Result refreshRemoteSchema(@RequestBody RefreshSchemaRequest request) {
        log.info("Refreshing remote schema: {} in env: {}", request.getSchemaName(), request.getEnv());
        try {
            boolean success = hasuraService.refreshRemoteSchema(request.getEnv(), request.getSchemaName());
            if (success) {
                return Result.ok("Schema refreshed successfully");
            } else {
                return Result.error("Failed to refresh schema");
            }
        } catch (Exception e) {
            log.error("Error refreshing remote schema", e);
            return Result.error("Failed to refresh schema: " + e.getMessage());
        }
    }
} 
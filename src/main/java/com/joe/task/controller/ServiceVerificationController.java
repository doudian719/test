package com.joe.task.controller;

import com.joe.task.config.TemporalConfig;
import com.joe.task.temporal.workflow.ServiceVerificationWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/temporal")
public class ServiceVerificationController {

    @Autowired
    private WorkflowClient workflowClient;
    
    @Autowired
    private WorkflowServiceStubs workflowServiceStubs;

    @PostMapping("/verify/{env}/{namespace}/{serviceName}")
    public ResponseEntity<String> startVerification(
            @PathVariable String env,
            @PathVariable String namespace,
            @PathVariable String serviceName) {
        log.info("Starting verification workflow for service: {} in namespace: {} env: {}", 
            serviceName, namespace, env);
        try {
            // Create workflow options
            WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                .setWorkflowId(String.format("verify-service-%s-%s-%s", env, namespace, serviceName))
                .build();

            // Start the workflow
            ServiceVerificationWorkflow workflow = workflowClient.newWorkflowStub(
                ServiceVerificationWorkflow.class,
                options);

            // Execute the workflow asynchronously
            WorkflowExecution execution = 
                WorkflowClient.start(workflow::verifyServiceWorkflow, env, namespace, serviceName);

            return ResponseEntity.ok()
                .body(String.format(
                    "Verification workflow started. WorkflowId: %s, RunId: %s", 
                    execution.getWorkflowId(), 
                    execution.getRunId()));

        } catch (Exception e) {
            log.error("Error starting verification workflow for service: {} in namespace: {} env: {}", 
                serviceName, namespace, env, e);
            return ResponseEntity.internalServerError()
                .body("Error starting workflow: " + e.getMessage());
        }
    }

    @GetMapping("/status/{workflowId}")
    public ResponseEntity<String> getWorkflowStatus(@PathVariable String workflowId) {
        try {
            // Get workflow stub
            WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub(workflowId);
            
            // Get workflow execution
            WorkflowExecution execution = workflowStub.getExecution();
            
            // Create describe request
            DescribeWorkflowExecutionRequest request = DescribeWorkflowExecutionRequest.newBuilder()
                .setNamespace("default")
                .setExecution(execution)
                .build();
            
            // Get workflow description
            DescribeWorkflowExecutionResponse response = 
                workflowServiceStubs.blockingStub().describeWorkflowExecution(request);
            
            return ResponseEntity.ok(String.format(
                "Workflow Status: %s", response.getWorkflowExecutionInfo().getStatus()));
                
        } catch (Exception e) {
            log.error("Error getting workflow status for ID: " + workflowId, e);
            return ResponseEntity.internalServerError()
                .body("Error getting workflow status: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/terminate/{workflowId}")
    public ResponseEntity<String> terminateWorkflow(@PathVariable String workflowId) {
        try {
            // Get workflow stub
            WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub(workflowId);
            
            // Terminate workflow
            workflowStub.terminate("Manually terminated");
            
            return ResponseEntity.ok("Workflow terminated successfully");
        } catch (Exception e) {
            log.error("Error terminating workflow: " + workflowId, e);
            return ResponseEntity.internalServerError()
                .body("Error terminating workflow: " + e.getMessage());
        }
    }
    
    @PostMapping("/signal/{workflowId}/retry")
    public ResponseEntity<String> signalWorkflowRetry(@PathVariable String workflowId) {
        try {
            // Get workflow stub
            WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub(workflowId);
            
            // Signal workflow to retry
            workflowStub.signal("RetrySignal");
            
            return ResponseEntity.ok("Retry signal sent successfully");
        } catch (Exception e) {
            log.error("Error sending retry signal to workflow: " + workflowId, e);
            return ResponseEntity.internalServerError()
                .body("Error sending retry signal: " + e.getMessage());
        }
    }
} 
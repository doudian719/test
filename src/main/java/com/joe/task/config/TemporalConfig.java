package com.joe.task.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    // Define the Temporal service target
    private static final String TEMPORAL_SERVICE_TARGET = "localhost:7233";
    
    // Define the task queue name
    public static final String TASK_QUEUE = "ServiceVerificationTaskQueue";

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        // Configure connection to Temporal service
        return WorkflowServiceStubs.newInstance(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(TEMPORAL_SERVICE_TARGET)
                .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs workflowServiceStubs) {
        // Create workflow client
        return WorkflowClient.newInstance(
            workflowServiceStubs,
            WorkflowClientOptions.newBuilder()
                .setNamespace("default")
                .build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient workflowClient) {
        // Create worker factory
        return WorkerFactory.newInstance(workflowClient);
    }
} 
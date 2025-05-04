package com.joe.task.temporal.workflow;

import com.joe.task.temporal.activity.ServiceVerificationActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ServiceVerificationWorkflowImpl implements ServiceVerificationWorkflow {
    
    // Define retry options
    private final RetryOptions retryOptions = RetryOptions.newBuilder()
        .setInitialInterval(Duration.ofSeconds(1))
        .setMaximumInterval(Duration.ofSeconds(10))
        .setBackoffCoefficient(2.0)
        .setMaximumAttempts(3)
        .build();
    
    // Define activity options with retry policy
    private final ActivityOptions options = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(30))
        .setRetryOptions(retryOptions)
        .build();
    
    // Create activities stub
    private final ServiceVerificationActivities activities = 
        Workflow.newActivityStub(ServiceVerificationActivities.class, options);
    
    @Override
    public boolean verifyServiceWorkflow(String env, String namespace, String serviceName) {
        // Execute activities in sequence with retries
        boolean ksvcResult = activities.checkKSVCInK8S(env, namespace, serviceName);
        if (!ksvcResult) {
            return false;
        }
        
        boolean hasuraResult = activities.checkWithHasura(env, namespace, serviceName);
        if (!hasuraResult) {
            return false;
        }
        
        return activities.verifyService(namespace, serviceName);
    }
} 
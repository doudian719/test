package com.joe.task.temporal.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ServiceVerificationWorkflow {
    
    @WorkflowMethod
    boolean verifyServiceWorkflow(String env, String namespace, String serviceName);
} 
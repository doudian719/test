package com.joe.task.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ServiceVerificationActivities {
    
    @ActivityMethod
    boolean checkKSVCInK8S(String env, String namespace, String serviceName);
    
    @ActivityMethod
    boolean checkWithHasura(String env, String namespace, String serviceName);
    
    @ActivityMethod
    boolean verifyService(String namespace, String serviceName);
} 
package com.joe.task.config;

import com.joe.task.job.sb.K8SLogMonitorJob;
import com.joe.task.job.sb.KubernetesJob;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobConfig implements ApplicationContextAware {

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // Set the ApplicationContext for KubernetesJob
        KubernetesJob.setApplicationContext(applicationContext);
        K8SLogMonitorJob.setApplicationContext(applicationContext);
    }
} 
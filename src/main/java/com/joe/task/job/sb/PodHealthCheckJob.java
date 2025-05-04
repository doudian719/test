package com.joe.task.job.sb;

import ch.qos.logback.classic.Logger;
import com.joe.task.config.KubernetesClientManager;
import com.joe.task.job.BaseJob;
import com.joe.task.service.k8s.CommonHealthService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionException;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Map;

@DisallowConcurrentExecution
public class PodHealthCheckJob extends BaseJob {
    private static final Logger logger = (Logger) LoggerFactory.getLogger(PodHealthCheckJob.class);
    private static ApplicationContext applicationContext;
    private CommonHealthService commonHealthService;

    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
    }

    public PodHealthCheckJob() {
        super(PodHealthCheckJob.class, logger);
        if (applicationContext != null) {
            this.commonHealthService = applicationContext.getBean(CommonHealthService.class);
        }
    }

    public void triggerSchema(String parameter) throws JobExecutionException {
        info("Starting Pod health check...");
        
        Map<String, Object> paramMap = extractParameter(parameter);
        String envStr = (String) paramMap.get("env");
        String env = envStr.toUpperCase();
        
        try {
            commonHealthService.checkPodsHealth(env);
        } catch (Exception e) {
            error("Pod health check failed");
            throw new JobExecutionException(e);
        }
    }
} 
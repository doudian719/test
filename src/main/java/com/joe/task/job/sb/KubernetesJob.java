package com.joe.task.job.sb;

import ch.qos.logback.classic.Logger;
import com.joe.task.config.KubernetesClientManager;
import com.joe.task.job.BaseJob;
import com.joe.task.service.k8s.PodService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionException;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Map;

@DisallowConcurrentExecution
public class KubernetesJob extends BaseJob {
    private static final Logger logger = (Logger) LoggerFactory.getLogger(KubernetesJob.class);

    private static ApplicationContext applicationContext;

    public static void setApplicationContext(ApplicationContext context)
    {
        applicationContext = context;
    }

    public KubernetesJob()
    {
        super(KubernetesJob.class, logger);
        if (applicationContext != null)
        {
            this.podService = applicationContext.getBean(PodService.class);
        }
    }

    private PodService podService;

    public void triggerSchema(String parameter) throws JobExecutionException {
        info("Trigger KubernetesJob... with parameter" + parameter);

        Map<String, Object> paramMap = extractParameter(parameter);
        String value = (String) paramMap.get("keyword");
        info("传入的参数值是：" + value);

        String envStr = (String) paramMap.get("env");
        info("环境是：" + envStr);

        String namespace = (String) paramMap.get("namespace");
        info("命名空间是：" + namespace);

        String namespacePrefix = (String) paramMap.get("namespacePrefix");
        info("命名空间前缀是：" + namespacePrefix);

        int cutoffTimeInMinutes = (int) paramMap.get("cutoffTimeInMinutes");
        info("截止时间（分钟）是：" + cutoffTimeInMinutes);

        // 直接使用环境字符串，不再转换为枚举
        String env = envStr.toUpperCase();
        
        podService.getNamespaces(env);
    }



}

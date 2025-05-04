package com.joe.task.job.sb;

import ch.qos.logback.classic.Logger;
import com.joe.task.config.KubernetesClientManager;
import com.joe.task.job.BaseJob;
import com.joe.task.service.k8s.LogService;
import org.apache.commons.lang3.StringUtils;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionException;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.Optional;

@DisallowConcurrentExecution
public class K8SLogMonitorJob extends BaseJob {
    private static final Logger logger = (Logger) LoggerFactory.getLogger(K8SLogMonitorJob.class);

    private static ApplicationContext applicationContext;

    public static void setApplicationContext(ApplicationContext context)
    {
        applicationContext = context;
    }

    private LogService logService;

    public K8SLogMonitorJob()
    {
        super(K8SLogMonitorJob.class, logger);
        if (applicationContext != null)
        {
            this.logService = applicationContext.getBean(LogService.class);
        }
    }

    public void triggerSchema(String parameter) throws JobExecutionException {
        info("Trigger K8SLogMonitorJob.triggerSchema... with parameter" + parameter);
        Map<String, Object> paramMap = extractParameter(parameter);
        String logs = getLogsUsingParameters(paramMap);

        info("获取到的日志是：\n" + logs);
    }

    public void monitorErrorMessage(String parameter) throws JobExecutionException {
        info("Trigger K8SLogMonitorJob.monitorErrorMessage... with parameter" + parameter);
        Map<String, Object> paramMap = extractParameter(parameter);

        String keyword = (String) paramMap.get("keyword");
        info("传入的keyword 是：" + keyword);

        if(StringUtils.isEmpty(keyword))
        {
            error("monitorErrorMessage, keyword 不可以为空");
        }
        else
        {
            String logs = getLogsUsingParameters(paramMap);
            if(StringUtils.containsIgnoreCase(logs, keyword))
            {
                error("检测到错误关键字： " + keyword);
            }
            else
            {
                info("没有检测到关键字 " + keyword);
            }
        }
    }

    private String getLogsUsingParameters(Map<String, Object> paramMap) throws JobExecutionException {

        String envStr = (String) paramMap.get("env");
        info("环境是：" + envStr);

        String namespace = (String) paramMap.get("namespace");
        info("传入的namespace 是：" + namespace);

        String podName = (String) paramMap.get("podName");
        info("传入的podName 是：" + podName);

        String podLabel = (String) paramMap.get("podLabel");
        info("传入的podLabel 是：" + podLabel);

        String keyword = (String) paramMap.get("keyword");
        info("传入的keyword 是：" + keyword);

        // 直接使用环境字符串，不再转换为枚举
        String env = envStr.toUpperCase();

        return logService.getPodsLogs(
                env,
                namespace,
                Optional.ofNullable(podName),
                Optional.ofNullable(podLabel),
                Optional.ofNullable(keyword)
        );
    }
}

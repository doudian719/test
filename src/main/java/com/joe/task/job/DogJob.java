package com.joe.task.job;

import ch.qos.logback.classic.Logger;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionException;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.Map;

@DisallowConcurrentExecution
public class DogJob extends BaseJob
{
    private static final Logger logger = (Logger) LoggerFactory.getLogger(DogJob.class);

    public DogJob()
    {
        super(DogJob.class, logger);
    }

    public void triggerJenkins(String parameter) throws JobExecutionException
    {
        info("Trigger DogJob... with parameter" + parameter);

        Map<String, Object> paramMap = extractParameter(parameter);
        String value = (String) paramMap.get("TEST");
        info("传入的参数值是：" + value);

        LocalTime now = LocalTime.now();
        int second = now.getSecond();

        try
        {
            Thread.sleep(3000);
            int result = 100/(second%2);
            info("计算结果是: " + result);
        }
        catch (Exception e)
        {
            error("计算的时候出了错误了。。。。");
        }
    }
}

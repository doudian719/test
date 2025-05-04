package com.joe.task.job;

import ch.qos.logback.classic.Logger;
import lombok.SneakyThrows;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionException;
import org.slf4j.LoggerFactory;

import java.util.Map;

//@DisallowConcurrentExecution
public class TimerJob extends BaseJob
{
    private static final Logger logger = (Logger) LoggerFactory.getLogger(TimerJob.class);

    public TimerJob()
    {
        super(TimerJob.class, logger);
    }

    @SneakyThrows
    public void countDownInMinutes(String parameter) throws JobExecutionException
    {
        info("Trigger TimerJob... with parameter" + parameter);

        Map<String, Object> paramMap = extractParameter(parameter);
        String delay = (String) paramMap.get("delay");
        String message = (String) paramMap.get("message");

        info("传入的参数值是：" + delay);
        info("传入的参数值是：" + message);

        long minutes = 1000*60;
        Thread.sleep(minutes * Integer.valueOf(delay));
    }

    @SneakyThrows
    public void countDownInSeconds(String parameter) throws JobExecutionException
    {
        info("Trigger TimerJob... with parameter" + parameter);

        Map<String, Object> paramMap = extractParameter(parameter);
        String delay = (String) paramMap.get("delay");
        String message = (String) paramMap.get("message");

        info("传入的参数值是：" + delay);
        info("传入的参数值是：" + message);

        long seconds = 1000;
        Thread.sleep(seconds * Integer.valueOf(delay));
    }

}

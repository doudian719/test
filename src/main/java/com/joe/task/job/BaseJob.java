package com.joe.task.job;

import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.joe.task.repo.JobLogRepository;
import com.joe.task.util.DatabaseAppender;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;

public abstract class BaseJob implements Job, Serializable
{
    protected DatabaseAppender databaseAppender;

    protected Class<?> childClass;

    protected Logger logger;

    @Autowired
    private JobLogRepository jobLogRepository;

    public BaseJob(Class<?> clazz, Logger logger)
    {
        initialize(clazz, logger);
    }

    public void initialize(Class<?> clazz, Logger logger)
    {
        this.childClass = clazz;
        this.logger = logger;
    }

    private void before(JobExecutionContext jobExecutionContext)
    {
        databaseAppender = new DatabaseAppender(jobExecutionContext, jobLogRepository);
        databaseAppender.setContext(logger.getLoggerContext());
        databaseAppender.start();
        logger.addAppender(databaseAppender);
    }

    private void after()
    {
        logger.detachAppender(databaseAppender);
        databaseAppender.stop();
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException
    {
        before(jobExecutionContext);

        JobDetail jobDetail = jobExecutionContext.getJobDetail();
        JobDataMap dataMap = jobDetail.getJobDataMap();

        /**
         * 获取任务中保存的方法名字，动态调用方法
         */
        String methodName = dataMap.getString("jobMethodName");
        try
        {
            Object job = childClass.getDeclaredConstructor().newInstance();

            Method method = job.getClass().getMethod(methodName, String.class);

            String jobParameter = dataMap.getString("jobParameter");
            method.invoke(job, jobParameter);
        }
        catch (Exception e)
        {
            throw new JobExecutionException(e);
        }
        finally
        {
            after();
        }
    }

    protected void info(String message)
    {
        System.out.println(message);
        logger.info(message);
    }

    protected void error(String message) throws JobExecutionException
    {
        System.err.println(message);
        logger.info(message);
        throw new JobExecutionException();
    }

    protected Map<String, Object> extractParameter(String parameter) throws JobExecutionException
    {
        ObjectMapper objectMapper = new ObjectMapper();
        try
        {
            Map<String, Object> resultMap = objectMapper.readValue(parameter, Map.class);
            info("Converted JSON to Map: " + resultMap);

            return resultMap;
        }
        catch (IOException e)
        {
            error("Error while converting JSON to Map: " + e.getMessage());
        }

        return Maps.newHashMap();
    }

    protected void assertNotNull(Map<String, Object> paramMap, String... paramNames) throws JobExecutionException
    {
        try
        {
            for (String paramName : paramNames)
            {
                Assert.notNull(paramMap.get(paramName), "参数【" + paramName + "】 不可以为空！");
            }
        }
        catch (IllegalArgumentException e)
        {
            error("参数不合法！");
            throw new JobExecutionException();
        }
    }
}

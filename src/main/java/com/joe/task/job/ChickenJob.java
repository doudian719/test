package com.joe.task.job;

import ch.qos.logback.classic.Logger;
import org.quartz.DisallowConcurrentExecution;
import org.slf4j.LoggerFactory;

/**
 * 实现序列化接口、防止重启应用出现quartz Couldn't retrieve job because a required class was not found 的问题
 * Job 的实例要到该执行它们的时候才会实例化出来。每次 Job 被执行，一个新的 Job 实例会被创建。
 * 其中暗含的意思就是你的 Job 不必担心线程安全性，因为同一时刻仅有一个线程去执行给定 Job 类的实例，甚至是并发执行同一 Job 也是如此。
 * @DisallowConcurrentExecution 保证上一个任务执行完后，再去执行下一个任务，这里的任务是同一个任务
 */
@DisallowConcurrentExecution
public class ChickenJob extends BaseJob {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = (Logger) LoggerFactory.getLogger(ChickenJob.class);

    public ChickenJob() {
        super(ChickenJob.class, logger);
    }

    public void test1(String parameter)
    {
        System.out.println("测试方法1 with parameter:" + parameter);
    }

    public void test2(String parameter)
    {
        System.out.println("测试方法2 with parameter:" + parameter);
    }


}
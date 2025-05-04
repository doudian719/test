package com.joe.task.config;

import ch.qos.logback.classic.Logger;
import com.joe.task.entity.QuartzEntity;
import com.joe.task.listener.JobExecutionListener;
import com.joe.task.service.IJobService;
import lombok.SneakyThrows;
import org.quartz.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TaskRunner implements ApplicationRunner{

    private static final Logger logger = (Logger) LoggerFactory.getLogger(TaskRunner.class);

    @Autowired
    private IJobService jobService;
    @Autowired
    private Scheduler scheduler;

    @Autowired
    private JobExecutionListener jobExecutionListener;

    @Override
    public void run(ApplicationArguments var) throws Exception{
        scheduler.getListenerManager().addJobListener(jobExecutionListener);

        /**
         * 系统启动的时候会初始化一个任务
         */
        Long count = jobService.listQuartzEntity(null);
        if(count<10)
        {
//            addDefaultTasks(10);
        }
    }


    @SneakyThrows
    private void addDefaultTasks(int size)
    {
        for (int i = 0; i < size; i++)
        {
            logger.info("初始化测试任务 B Task" + i);
            QuartzEntity quartz = new QuartzEntity();
            quartz.setJobName("testB" + i);
            quartz.setJobGroup("testB");
            quartz.setDescription("测试任务");
            quartz.setJobClassName("com.joe.task.job.ChickenJob");
            quartz.setCronExpression("0 0 0 20 4 FRI");
            quartz.setJobMethodName("test1");
            quartz.setJobParameter("test parameter");
            Class cls = Class.forName(quartz.getJobClassName()) ;
            cls.newInstance();
            //构建job信息
            JobDetail job = JobBuilder.newJob(cls).withIdentity(quartz.getJobName(),
                            quartz.getJobGroup())
                    .withDescription(quartz.getDescription()).build();
            job.getJobDataMap().put("jobMethodName", "test1");
            job.getJobDataMap().put("jobParameter", "test1_parameter");
            // 触发时间点
            CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(quartz.getCronExpression());
            Trigger trigger = TriggerBuilder.newTrigger().withIdentity("trigger"+quartz.getJobName(), quartz.getJobGroup())
                    .startNow().withSchedule(cronScheduleBuilder).build();
            //交由Scheduler安排触发
            scheduler.scheduleJob(job, trigger);
        }
    }
}
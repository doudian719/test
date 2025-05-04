package com.joe.task.service.impl;

import com.joe.task.dynamicquery.DynamicQuery;
import com.joe.task.entity.PageBean;
import com.joe.task.entity.QuartzEntity;
import com.joe.task.entity.Result;
import com.joe.task.service.IJobService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service("jobService")
@Slf4j
public class JobServiceImpl implements IJobService
{
	@Autowired
	private DynamicQuery dynamicQuery;
    @Autowired
    private Scheduler scheduler;

	@Override
	public Result listQuartzEntity(QuartzEntity quartz, Integer pageNo, Integer pageSize) throws SchedulerException
    {
	    String countSql = "SELECT COUNT(*) FROM qrtz_job_details AS job WHERE 1 = 1 ";
        if(StringUtils.isNotEmpty(quartz.getJobName())){
            countSql+=" AND LOWER(job.JOB_NAME) LIKE '%" + quartz.getJobName().toLowerCase() + "%'";
        }
        if(StringUtils.isNotEmpty(quartz.getJobGroup())){
            countSql+=" AND LOWER(job.JOB_GROUP) LIKE '%" + quartz.getJobGroup().toLowerCase() + "%'";
        }

        Long totalCount = dynamicQuery.nativeQueryCount(countSql);
        PageBean<QuartzEntity> data = new PageBean<>();
        if(totalCount>0){
            StringBuffer nativeSql = new StringBuffer();

            nativeSql.append("SELECT job.JOB_NAME as \"jobName\",job.JOB_GROUP as \"jobGroup\", job.DESCRIPTION as \"description\", job.JOB_CLASS_NAME as \"jobClassName\", ");
            nativeSql.append("cron.CRON_EXPRESSION as \"cronExpression\", tri.TRIGGER_NAME as \"triggerName\", tri.TRIGGER_STATE as \"triggerState\", ");
            nativeSql.append("job.JOB_NAME as \"oldJobName\", job.JOB_GROUP as \"oldJobGroup\" ");
            nativeSql.append("FROM qrtz_job_details AS job ");
            nativeSql.append("LEFT JOIN qrtz_triggers AS tri ON job.JOB_NAME = tri.JOB_NAME  AND job.JOB_GROUP = tri.JOB_GROUP ");
            nativeSql.append("LEFT JOIN qrtz_cron_triggers AS cron ON cron.TRIGGER_NAME = tri.TRIGGER_NAME AND cron.TRIGGER_GROUP= tri.JOB_GROUP ");
            nativeSql.append("WHERE tri.TRIGGER_TYPE = 'CRON' ");
            if(StringUtils.isNotEmpty(quartz.getJobName())){
                nativeSql.append("AND LOWER(job.JOB_NAME) LIKE '%").append(quartz.getJobName().toLowerCase()).append("%' ");
            }
            if(StringUtils.isNotEmpty(quartz.getJobGroup())){
                nativeSql.append("AND LOWER(job.JOB_GROUP) LIKE '%").append(quartz.getJobGroup().toLowerCase()).append("%' ");
            }
            if(StringUtils.isNotEmpty(quartz.getDescription())){
                nativeSql.append("AND LOWER(job.DESCRIPTION) LIKE '%").append(quartz.getDescription().toLowerCase()).append("%' ");
            }

            Pageable pageable = PageRequest.of(pageNo-1,pageSize);
            List<QuartzEntity> list = dynamicQuery.nativeQueryPagingList(QuartzEntity.class, pageable, nativeSql.toString());
            for (QuartzEntity quartzEntity : list) {
                JobKey key = new JobKey(quartzEntity.getJobName(), quartzEntity.getJobGroup());
                JobDetail jobDetail = scheduler.getJobDetail(key);
                log.info("jobDetails........" + jobDetail);

                quartzEntity.setJobMethodName(jobDetail.getJobDataMap().getString("jobMethodName"));
                quartzEntity.setJobParameter(jobDetail.getJobDataMap().getString("jobParameter"));
            }
            data = new PageBean<>(list, totalCount);
        }
        return Result.ok(data);
	}

	@Override
	public Long listQuartzEntity(QuartzEntity quartz)
    {
		StringBuffer nativeSql = new StringBuffer();
		nativeSql.append("SELECT COUNT(*) ");
		nativeSql.append("FROM qrtz_job_details AS job LEFT JOIN qrtz_triggers AS tri ON job.JOB_NAME = tri.JOB_NAME ");
		nativeSql.append("LEFT JOIN qrtz_cron_triggers AS cron ON cron.TRIGGER_NAME = tri.TRIGGER_NAME ");
		nativeSql.append("WHERE tri.TRIGGER_TYPE = 'CRON' ");
		return dynamicQuery.nativeQueryCount(nativeSql.toString(), new Object[]{});
	}

    @Override
    @Transactional
    public void save(QuartzEntity quartz) throws Exception
    {
        //如果是修改 展示旧的 任务
        if(quartz.getOldJobGroup()!=null)
        {
            JobKey key = new JobKey(quartz.getOldJobName(),quartz.getOldJobGroup());
            scheduler.deleteJob(key);
        }
        initialJobDetails(quartz);
    }

    @Override
    public void copy(QuartzEntity quartz) throws Exception
    {
        String countSql = "SELECT COUNT(*) FROM qrtz_job_details AS job WHERE job.JOB_NAME = '" + StringUtils.trim(quartz.getJobName())
                + "' AND job.JOB_GROUP = '"+ StringUtils.trim(quartz.getJobGroup()) + "'";
        Long totalCount = dynamicQuery.nativeQueryCount(countSql);

        if(totalCount > 0)
        {
            throw new Exception("Job already exists.");
        }

        initialJobDetails(quartz);
    }

    private void initialJobDetails(QuartzEntity quartz) throws ClassNotFoundException, InstantiationException, IllegalAccessException, SchedulerException
    {
        Class cls = Class.forName(quartz.getJobClassName()) ;
        cls.newInstance();
        //构建job信息
        JobDetail job = JobBuilder.newJob(cls).withIdentity(quartz.getJobName(),
                        quartz.getJobGroup())
                .withDescription(quartz.getDescription()).build();
        job.getJobDataMap().put("jobMethodName", quartz.getJobMethodName());
        job.getJobDataMap().put("jobParameter", quartz.getJobParameter());
        // 触发时间点
        CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(quartz.getCronExpression());
        Trigger trigger = TriggerBuilder.newTrigger().withIdentity("trigger"+ quartz.getJobName(), quartz.getJobGroup())
                .startNow().withSchedule(cronScheduleBuilder).build();
        //交由Scheduler安排触发
        scheduler.scheduleJob(job, trigger);

        // 默认先暂停
        JobKey key = new JobKey(quartz.getJobName(),quartz.getJobGroup());
        scheduler.pauseJob(key);
    }
}

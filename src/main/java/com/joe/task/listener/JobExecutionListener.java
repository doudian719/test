package com.joe.task.listener;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.joe.task.config.WebSocketController;
import com.joe.task.constant.NotificationType;
import com.joe.task.entity.JobLog;
import com.joe.task.repo.JobLogRepository;
import lombok.RequiredArgsConstructor;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobExecutionListener extends JobListenerSupport
{
    private final JobLogRepository jobLogRepository;

    @Override
    public String getName()
    {
        return "JobExecutionListener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext jobExecutionContext)
    {
        JobLog record = new JobLog();
        record.setJobName(jobExecutionContext.getJobDetail().getKey().getName());
        record.setJobGroup(jobExecutionContext.getJobDetail().getKey().getGroup());

        // 取出Job Description，用于弹出的窗口上面
        record.setJobDescription(jobExecutionContext.getJobDetail().getDescription());

        // 如果一个job需要在成功的时候也弹出提示，需要设置参数 notify_when_success:true
        JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        if(!jobDataMap.isEmpty()) {
            if(jobDataMap.containsKey("jobParameter")) {
                String jobParameter = (String) jobDataMap.get("jobParameter");
                JSONObject json = JSONUtil.parseObj(jobParameter);
                if(json.containsKey("notify_when_success")) {
                    Object notifyWhenSuccessObject = json.get("notify_when_success");
                    if(notifyWhenSuccessObject instanceof Boolean) {
                        boolean notify = (Boolean) notifyWhenSuccessObject;
                        record.setNotifyWhenSuccess(notify);
                    }
                }
            }
        }

        record.setStartTime(Timestamp.from(Instant.now()));
        record.setStatus("RUNNING");

        JobLog savedRecord = jobLogRepository.save(record);
        jobExecutionContext.put("recordId", savedRecord.getId());
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException)
    {
        Long recordId = (Long) context.get("recordId");
        if (recordId != null)
        {
            Optional<JobLog> optionalRecord = jobLogRepository.findById(recordId);

            if (optionalRecord.isPresent())
            {
                JobLog record = optionalRecord.get();
                record.setEndTime(Timestamp.from(Instant.now()));

                if(null == jobException)
                {
                    record.setStatus("SUCCESS");
                    if(record.isNotifyWhenSuccess())
                    {
                        // job参数里面包含了notify_when_success=true 的时候，才会给成功的job弹出提示框
                        WebSocketController.sendMessage(NotificationType.TYPE_INFO, record.getJobGroup()+"."+ record.getJobName(), record.getJobDescription());
                    }
                }
                else
                {
                    record.setStatus("FAILURE");
                    WebSocketController.sendMessage(NotificationType.TYPE_ERROR, record.getJobGroup()+"."+ record.getJobName(), record.getJobDescription());
                }

                jobLogRepository.save(record);
            }
        }
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context)
    {
        System.out.println("Job execution vetoed: " + context.getJobDetail().getKey().getName());
    }
}
package com.joe.task.service;

import com.joe.task.entity.JobLog;
import com.joe.task.entity.Result;
import org.quartz.SchedulerException;

public interface IJobLogService {

    Result listJobLogs(JobLog jobLog, Integer pageNo, Integer pageSize);

    Result clearLogs(JobLog jobLog);

    Result countJobLogs();
}

package com.joe.task.web;


import ch.qos.logback.classic.Logger;
import com.joe.task.entity.JobLog;
import com.joe.task.entity.QuartzEntity;
import com.joe.task.entity.Result;
import com.joe.task.service.IJobLogService;
import com.joe.task.service.IJobService;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

@RestController

@RequestMapping("/joblog")
public class JobLogController {

	private static final Logger logger = (Logger) LoggerFactory.getLogger(JobLogController.class);

    @Autowired
    private IJobLogService jobLogService;

	@PostMapping("/list")
	public Result list(JobLog jobLog, Integer pageNo, Integer pageSize) throws SchedulerException {
		logger.info("日志列表");
		return jobLogService.listJobLogs(jobLog, pageNo, pageSize);
	}

	@PostMapping("/clear")
	public Result clear(JobLog jobLog) throws SchedulerException {
		logger.info("清除所有的日志列表");
		return jobLogService.clearLogs(jobLog);
	}

}

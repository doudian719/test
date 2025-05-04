package com.joe.task.service;

import com.joe.task.entity.QuartzEntity;
import com.joe.task.entity.Result;
import org.quartz.SchedulerException;

public interface IJobService {

    Result listQuartzEntity(QuartzEntity quartz, Integer pageNo, Integer pageSize) throws SchedulerException;
    
    Long listQuartzEntity(QuartzEntity quartz);

    void save(QuartzEntity quartz) throws Exception;

    void copy(QuartzEntity quartz) throws Exception;
}

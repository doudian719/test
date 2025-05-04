package com.joe.task.service.impl;

import com.joe.task.dynamicquery.DynamicQuery;
import com.joe.task.entity.JobLog;
import com.joe.task.entity.PageBean;
import com.joe.task.entity.Result;
import com.joe.task.repo.JobLogRepository;
import com.joe.task.service.IJobLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service("jobLogService")
@Slf4j
public class JobLogServiceImpl implements IJobLogService
{

	@Autowired
	private DynamicQuery dynamicQuery;

    @Autowired
    private JobLogRepository jobLogRepository;

    @Override
    public Result listJobLogs(JobLog jobLog, Integer pageNo, Integer pageSize)
    {
        String countSql = "SELECT COUNT(*) FROM job_log AS job WHERE 1 = 1 ";

        if(StringUtils.isNotEmpty(jobLog.getJobName())){
            countSql+=" AND LOWER(job.JOB_NAME) LIKE '%" + jobLog.getJobName().toLowerCase() + "%' ";
        }
        if(StringUtils.isNotEmpty(jobLog.getJobGroup())){
            countSql+=" AND LOWER(job.JOB_GROUP) LIKE '%" + jobLog.getJobGroup().toLowerCase() + "%' ";
        }
        if(StringUtils.isNotEmpty(jobLog.getStatus())){
            countSql+=" AND job.STATUS = '" + jobLog.getStatus() + "' ";
        }

        Long totalCount = dynamicQuery.nativeQueryCount(countSql);
        PageBean<JobLog> data = new PageBean<>();
        if(totalCount>0){
            StringBuilder nativeSql = new StringBuilder();
            nativeSql.append("SELECT job.ID as \"id\", job.JOB_NAME as \"jobName\", job.JOB_GROUP as \"jobGroup\", job.START_TIME as \"startTime\", job.END_TIME as \"endTime\", job.STATUS as \"status\", job.LOGS as \"logs\" ");
            nativeSql.append("FROM job_log as job WHERE 1 = 1 ");

            if(StringUtils.isNotEmpty(jobLog.getJobName())){
                nativeSql.append("AND LOWER(job.JOB_NAME) LIKE '%").append(jobLog.getJobName().toLowerCase()).append("%' ");
            }
            if(StringUtils.isNotEmpty(jobLog.getJobGroup())){
                nativeSql.append("AND LOWER(job.JOB_GROUP) LIKE '%").append(jobLog.getJobGroup().toLowerCase()).append("%' ");
            }
            if(StringUtils.isNotEmpty(jobLog.getStatus())){
                nativeSql.append("AND job.STATUS = '" + jobLog.getStatus() + "' ");
            }

            nativeSql.append("ORDER BY job.ID DESC ");

            Pageable pageable = PageRequest.of(pageNo-1,pageSize);
            List<JobLog> list = dynamicQuery.nativeQueryPagingList(JobLog.class, pageable, nativeSql.toString());

            data = new PageBean<>(list, totalCount);
        }
        return Result.ok(data);
    }

    @Override
    public Result clearLogs(JobLog jobLog)
    {
        StringBuilder nativeSql = new StringBuilder();
        nativeSql.append("SELECT job.ID as \"id\", job.JOB_NAME as \"jobName\", job.JOB_GROUP as \"jobGroup\", job.START_TIME as \"startTime\", job.END_TIME as \"endTime\", job.STATUS as \"status\", job.LOGS as \"logs\" ");
        nativeSql.append("FROM job_log as job WHERE 1 = 1 ");

        if(null != jobLog.getId()){
            nativeSql.append("AND job.ID = " + jobLog.getId().longValue() + " ");
        }

        if(StringUtils.isNotEmpty(jobLog.getJobName())){
            nativeSql.append("AND LOWER(job.JOB_NAME) LIKE '%").append(jobLog.getJobName().toLowerCase()).append("%' ");
        }
        if(StringUtils.isNotEmpty(jobLog.getJobGroup())){
            nativeSql.append("AND LOWER(job.JOB_GROUP) LIKE '%").append(jobLog.getJobGroup().toLowerCase()).append("%' ");
        }
        if(StringUtils.isNotEmpty(jobLog.getStatus())){
            nativeSql.append("AND job.STATUS = '" + jobLog.getStatus() + "' ");
        }

        nativeSql.append("ORDER BY job.ID DESC ");

        Pageable pageable = PageRequest.of(0, 5000);
        List<JobLog> list = dynamicQuery.nativeQueryPagingList(JobLog.class, pageable, nativeSql.toString());


        jobLogRepository.deleteAll(list);

        return Result.ok();
    }

    @Override
    public Result countJobLogs()
    {
        String nativeSql = "select STATUS as status, count(*) as total FROM job_log group by STATUS";
        Map<String, Long> resultMap = dynamicQuery.nativeCustomQuery(nativeSql, "status", "total");
        return Result.ok(resultMap);
    }

}

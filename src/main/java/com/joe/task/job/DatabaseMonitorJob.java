package com.joe.task.job;

import ch.qos.logback.classic.Logger;
import com.joe.task.util.DatabaseConnectionManager;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionException;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalTime;
import java.util.Map;

@DisallowConcurrentExecution
public class DatabaseMonitorJob extends BaseJob
{
    private static final Logger logger = (Logger) LoggerFactory.getLogger(DatabaseMonitorJob.class);

    public DatabaseMonitorJob()
    {
        super(DatabaseMonitorJob.class, logger);
    }

    public void dataChange(String parameter) throws JobExecutionException
    {
        info("Trigger DatabaseMonitorJob dataChange... with parameter" + parameter);

        Map<String, Object> paramMap = extractParameter(parameter);

        // 确保需要的
        assertNotNull(paramMap, "env", "sql", "count");

        String env = (String) paramMap.get("env");
        String sql = (String) paramMap.get("sql");
        int count = (Integer) paramMap.get("count");

        DataSource dataSource = DatabaseConnectionManager.getInstance().getDataSource(env);
        if(null == dataSource)
        {
            error("无法连接数据库， env = " + env);
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        if(result.intValue() == count)
        {
            info("Database no change at " + LocalTime.now());
        }
        else
        {
            error("数据库有变化，before: " + count + ", after: " + result);
        }
    }
}

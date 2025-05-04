package com.joe.task.job;

import ch.qos.logback.classic.Logger;
import com.joe.task.constant.DBEnvName;
import com.joe.task.util.DatabaseConnectionManager;
import org.apache.commons.lang3.StringUtils;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionException;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@DisallowConcurrentExecution
public class SgrrJob extends BaseJob
{
    private static final Logger logger = (Logger) LoggerFactory.getLogger(SgrrJob.class);

    public SgrrJob()
    {
        super(SgrrJob.class, logger);
    }

    public void checkStatus(String parameter) throws JobExecutionException
    {
        info("Trigger SGRRJob... with parameter" + parameter);

        Map<String, Object> paramMap = extractParameter(parameter);
        String value = (String) paramMap.get("TEST");
        info("传入的参数值是：" + value);

        DataSource cmDataSource = DatabaseConnectionManager.getInstance().getDataSource(DBEnvName.PRD_EKS_CM);
        JdbcTemplate cmJdbcTemplate = new JdbcTemplate(cmDataSource);
        String cmSQL = getSQLForCloudManage();

        List<Map<String, Object>> mapList = cmJdbcTemplate.queryForList(cmSQL);
        if(mapList != null && !mapList.isEmpty())
        {
            DataSource sreDataSource = DatabaseConnectionManager.getInstance().getDataSource(DBEnvName.SRE_DB);
            NamedParameterJdbcTemplate sreJdbcTemplate = new NamedParameterJdbcTemplate(sreDataSource);
            String sreSql = getSQLForSRE();

            for (Map<String, Object> dataMap : mapList)
            {
                String accountId = (String) dataMap.get("account_id");
                String ruleIds = (String) dataMap.get("rule_ids");
                List<String> ruleIdList = Arrays.asList(StringUtils.split(ruleIds, ","));

                MapSqlParameterSource parameters = new MapSqlParameterSource();
                parameters.addValue("accountId", accountId);
                parameters.addValue("ruleIds", ruleIdList);

                RowMapper<String> rowMapper = (rs, rowNum) -> rs.getString("ruleid");

                List<String> sreDataList = sreJdbcTemplate.query(sreSql, parameters, rowMapper);
                if(ruleIdList.size() == sreDataList.size())
                {
                    System.out.println("SGRR rules for account [" + accountId + "] is still pending deletion");

                    System.out.println("Cloud Manage, ruleIdList.size() = " + ruleIdList.size());
                    System.out.println("SRE, sreDataList.size() = " + sreDataList.size());
                }
                else
                {
                    int deletedCount = ruleIdList.size() - sreDataList.size();
                    System.out.println("Deleted rules count = [" + deletedCount + "]");

                    List<String> reduce2 = ruleIdList.stream().filter(item -> !sreDataList.contains(item)).collect(toList());
                    System.out.println("---差集 reduce2 (list2 - list1)---");
                    reduce2.forEach(System.out :: println);

                    error("There are SGRR rules deleted for account: [" + accountId + "]");
                }
            }
        }
        else
        {
            info("No SGRR record pending deletion");
        }

    }

    private static String getSQLForCloudManage()
    {
        return "select ssg.account_id, string_agg(ssgr.id, ',') as rule_ids\n" +
                "from app_cloud_manage.sgrr_security_group_rule ssgr  \n" +
                "left join app_cloud_manage.sgrr_security_group ssg on ssg.id = ssgr.group_id \n" +
                "left join app_cloud_manage.stage s on s.cloud_account = ssg.account_id \n" +
                "where ssgr.recertify_status = 'REVOKE' and ssgr.revoke_status = 'REVOKE_IN_PROGRESS'\n" +
                // "and s.provision_status != 'DECOMMISSIONED'\n" +
                "and ssgr.window_id = 1\n" +
                "group by ssg.account_id";
    }

    private static String getSQLForSRE()
    {
        return "select ruleid from main.v_sgrule where accountid = :accountId and ruleid in (:ruleIds)";
    }
}

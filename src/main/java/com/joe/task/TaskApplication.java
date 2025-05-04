package com.joe.task;

import ch.qos.logback.classic.Logger;
import com.joe.task.util.DatabaseConnectionManager;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TaskApplication
{
	private static final Logger logger = (Logger) LoggerFactory.getLogger(TaskApplication.class);
	
	public static void main(String[] args)
	{
		SpringApplication.run(TaskApplication.class, args);
		logger.info("Started Task Application Successfully");
	}

	@Bean
	public DataSourceCloseHandler dataSourceCloseHandler()
	{
		return new DataSourceCloseHandler();
	}

	/**
	 * 退出SpringBoot 的时候，关闭其它所有的数据库连接
	 */
	private static class DataSourceCloseHandler implements DisposableBean
	{
		@Override
		public void destroy() throws Exception
		{
			DatabaseConnectionManager.getInstance().closeAllDataSources();
		}
	}
}
package com.joe.task.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class QuartzConfig {
    // Remove the previous datasource configuration
    // Spring Boot will handle the datasource configuration automatically
} 
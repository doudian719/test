package com.joe.task.entity;

import lombok.Data;

@Data
public class QuartzEntity{

    private String jobName;//任务名称
    private String jobGroup;//任务分组
    private String description;//任务描述
    private String jobClassName;//执行类
    private String jobMethodName;//执行方法
    private String jobParameter;//执行参数
    private String cronExpression;//执行时间
    private String triggerName;//执行时间
    private String triggerState;//任务状态

    private String oldJobName;//任务名称 用于修改
    private String oldJobGroup;//任务分组 用于修改
}

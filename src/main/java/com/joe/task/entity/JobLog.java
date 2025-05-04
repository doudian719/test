package com.joe.task.entity;

import lombok.Data;

import jakarta.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "job_log")
@Data
public class JobLog
{
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "job_group")
    private String jobGroup;

    @Column(name = "job_description")
    private String jobDescription;

    @Column(name = "start_time")
    private Timestamp startTime;

    @Column(name = "end_time")
    private Timestamp endTime;

    @Column(name = "status")
    private String status;

    @Column(name = "notify_when_success")
    private boolean notifyWhenSuccess;

    @Column(name = "logs", columnDefinition = "TEXT")
    private String logs;
}

package com.joe.task.entity;

import lombok.Data;

import java.sql.Timestamp;

import jakarta.persistence.*;

/**
 * Entity class for storing Azure DevOps (ADO) related information
 */
@Data
@Entity
@Table(name = "ado_info")
public class AdoInfo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "build_id")
    private Integer buildId;

    @Column(name = "repo_name")
    private String repoName;

    @Column(name = "repo_url")
    private String repoUrl;

    @Column(name = "pipeline_url")
    private String pipelineUrl;

    @Column(name = "last_sync_timestamp")
    private Timestamp lastSyncTimestamp;
} 
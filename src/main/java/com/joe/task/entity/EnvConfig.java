package com.joe.task.entity;

import jakarta.persistence.*;

/**
 * Environment configuration entity
 */
@Entity
@Table(name = "env_config")
public class EnvConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "k8s_server_host")
    private String k8sServerHost;

    @Column(name = "k8s_token", columnDefinition = "TEXT")
    private String k8sToken;

    @Column
    private Integer sequence;

    @Column(name = "is_hidden")
    private Boolean isHidden = false;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getK8sServerHost() {
        return k8sServerHost;
    }

    public void setK8sServerHost(String k8sServerHost) {
        this.k8sServerHost = k8sServerHost;
    }

    public String getK8sToken() {
        return k8sToken;
    }

    public void setK8sToken(String k8sToken) {
        this.k8sToken = k8sToken;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public Boolean getIsHidden() {
        return isHidden;
    }

    public void setIsHidden(Boolean isHidden) {
        this.isHidden = isHidden;
    }
} 
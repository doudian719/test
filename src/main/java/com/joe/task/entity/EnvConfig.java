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

    // Server URL for the environment
    @Column(name = "server_url")
    private String serverUrl;

    // Token for the environment
    @Column(name = "token", columnDefinition = "TEXT")
    private String token;

    @Column
    private Integer sequence;

    @Column(name = "is_hidden")
    private Boolean isHidden = false;

    // Resource type reference (foreign key)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource", referencedColumnName = "id")
    private ResourceType resource;

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

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
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

    public ResourceType getResource() {
        return resource;
    }

    public void setResource(ResourceType resource) {
        this.resource = resource;
    }
} 
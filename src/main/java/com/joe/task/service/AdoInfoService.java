package com.joe.task.service;

import com.joe.task.entity.AdoInfo;
import com.joe.task.repository.AdoInfoRepository;
import com.joe.task.cache.AdoInfoCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.azd.build.types.BuildDefinition;
import org.azd.build.types.BuildDefinitions;
import org.azd.build.types.Builds;
import org.azd.exceptions.AzDException;
import org.azd.git.types.GitRepository;
import org.azd.git.types.Repositories;
import org.azd.utils.AzDClientApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;

@Slf4j
@Service
public class AdoInfoService {
    
    @Autowired
    private AdoInfoRepository adoInfoRepository;

    @Autowired
    private AdoInfoCache adoInfoCache;

    @Value("${azure.devops.organization}")
    private String organization;

    @Value("${azure.devops.personal-access-token}")
    private String personalAccessToken;

    @Value("${azure.devops.project}")
    private String project;

    @Transactional
    public void batchInsert(List<AdoInfo> adoInfoList) {
        // First delete all existing records to maintain fresh sync
        adoInfoRepository.deleteAll();
        // Then insert all new records
        adoInfoRepository.saveAll(adoInfoList);
    }

    /**
     * Sync repositories from Azure DevOps
     * @return List of synced repositories
     */
    public List<AdoInfo> syncRepositories() throws AzDException {
        // Initialize Azure DevOps client
        AzDClientApi client = new AzDClientApi(organization, project, personalAccessToken);
        
        // Get all Git repositories
        Repositories repositories = client.getGitApi().getRepositories();
        List<AdoInfo> adoInfoList = new ArrayList<>();
        
        // For each repository, get its pipeline definitions
        for (GitRepository repo : repositories.getRepositories()) {
            String repoName = repo.getName();
            if(!StringUtils.startsWith(repoName, "55313")) {
                continue;
            }

            BuildDefinitions buildDefs = client.getBuildApi().getBuildDefinitions(repoName);
            
            if (buildDefs != null && buildDefs.getBuildDefinitions() != null) {
                for (BuildDefinition pipeline : buildDefs.getBuildDefinitions()) {
                    AdoInfo adoInfo = new AdoInfo();
                    adoInfo.setRepoName(repoName);
                    adoInfo.setRepoUrl(repo.getWebUrl());
                    adoInfo.setBuildId(pipeline.getId());
                    
                    // 构建Pipeline运行页面URL
                    String pipelineUrl = String.format("https://dev.azure.com/%s/%s/_build?definitionId=%d",
                            organization,
                            project,
                            pipeline.getId());
                    adoInfo.setPipelineUrl(pipelineUrl);
                    adoInfo.setLastSyncTimestamp(new Timestamp(System.currentTimeMillis()));
                    
                    adoInfoList.add(adoInfo);
                }
            }
        }
        
        // Save to database
        adoInfoRepository.saveAll(adoInfoList);
        
        // Update cache
        adoInfoCache.updateCache(adoInfoList);
        
        log.info("Successfully synced {} repositories with pipelines", adoInfoList.size());
        return adoInfoList;
    }

    /**
     * Find all repositories with pagination
     * @param pageNo page number (1-based)
     * @param pageSize size of each page
     * @return Page of repositories
     */
    public Page<AdoInfo> findAll(int pageNo, int pageSize) {
        // Convert to 0-based page number
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize);
        return adoInfoRepository.findAll(pageable);
    }

    /**
     * Search repositories by name with pagination
     * @param keyword search keyword
     * @param pageNo page number (1-based)
     * @param pageSize size of each page
     * @return Page of matching repositories
     */
    public Page<AdoInfo> searchByRepoName(String keyword, int pageNo, int pageSize) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll(pageNo, pageSize);
        }
        
        // Convert to 0-based page number
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize);
        return adoInfoRepository.findByRepoNameContainingIgnoreCase(keyword, pageable);
    }
} 
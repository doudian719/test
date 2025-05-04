package com.joe.task.web;

import com.joe.task.entity.AdoInfo;
import com.joe.task.entity.PageBean;
import com.joe.task.entity.Result;
import com.joe.task.service.AdoInfoService;
import lombok.extern.slf4j.Slf4j;
import org.azd.exceptions.AzDException;
import org.azd.utils.AzDClientApi;
import org.azd.workitemtracking.types.WorkItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ado")
public class AzureController
{
    @Autowired
    private AdoInfoService adoInfoService;

    @PostMapping("/repo/sync")
    public Result syncRepositories() {
        try {
            List<AdoInfo> adoInfoList = adoInfoService.syncRepositories();
            return Result.ok("Successfully synced " + adoInfoList.size() + " repositories");
        } catch (Exception e) {
            log.error("Error during repository sync: ", e);
            return Result.error("Error during repository sync: " + e.getMessage());
        }
    }

    @GetMapping("/repo/list")
    public Result listRepositories(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            Page<AdoInfo> page = adoInfoService.findAll(pageNo, pageSize);
            PageBean<AdoInfo> pageBean = new PageBean<>(page.getContent(), page.getTotalElements());
            pageBean.setPageNo(pageNo);
            pageBean.setPageSize(pageSize);
            return Result.ok(pageBean);
        } catch (Exception e) {
            log.error("Error listing repositories: {}", e.getMessage());
            return Result.error("Error listing repositories: " + e.getMessage());
        }
    }

    @GetMapping("/repo/search")
    public Result searchRepositories(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            Page<AdoInfo> page = adoInfoService.searchByRepoName(keyword, pageNo, pageSize);
            PageBean<AdoInfo> pageBean = new PageBean<>(page.getContent(), page.getTotalElements());
            pageBean.setPageNo(pageNo);
            pageBean.setPageSize(pageSize);
            return Result.ok(pageBean);
        } catch (Exception e) {
            log.error("Error searching repositories with keyword '{}': {}", keyword, e.getMessage());
            return Result.error("Error searching repositories: " + e.getMessage());
        }
    }

    // 示例：获取工作项
    public WorkItem getWorkItem(AzDClientApi azureClient, int workItemId) throws AzDException {
        try {
            return azureClient.getWorkItemTrackingApi().getWorkItem(workItemId);
        } catch (AzDException e) {
            log.error("Failed to get work item {}: {}", workItemId, e.getMessage());
            throw e;
        }
    }

    // 示例：创建工作项
    public WorkItem createWorkItem(AzDClientApi azureClient, String workItemType, Map<String, Object> fields) throws AzDException {
//        try {
//            return azureClient.getWorkItemTrackingApi().createWorkItem(workItemType, fields);
//        } catch (AzDException e) {
//            log.error("Failed to create work item of type {}: {}", workItemType, e.getMessage());
//            throw e;
//        }
        return null;
    }

    // 示例：更新工作项
    public void updateWorkItem(AzDClientApi azureClient, int workItemId, Map<String, Object> fields) throws AzDException {
        try {
            azureClient.getWorkItemTrackingApi().updateWorkItem(workItemId, fields);
            log.info("Successfully updated work item {}", workItemId);
        } catch (AzDException e) {
            log.error("Failed to update work item {}: {}", workItemId, e.getMessage());
            throw e;
        }
    }
}

package com.joe.task.web;

import com.joe.task.entity.Result;
import com.joe.task.service.IJobLogService;
import com.joe.task.service.IJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller

public class HomeController
{
    @Autowired
    private IJobService jobService;

    @Autowired
    private IJobLogService jobLogService;

    @GetMapping("/")
    public String home(Model model)
    {
        Long totalJobCount = jobService.listQuartzEntity(null);
        model.addAttribute("totalJobCount", totalJobCount);

        Result result = jobLogService.countJobLogs();
        Map<String, Long> map = (Map) result.get("msg");

        model.addAttribute("failureLogCount", map.containsKey("FAILURE")?map.get("FAILURE"):0);
        model.addAttribute("successLogCount", map.containsKey("SUCCESS")?map.get("SUCCESS"):0);
        model.addAttribute("runningLogCount", map.containsKey("RUNNING")?map.get("RUNNING"):0);

        // DatabaseConnectionManager.getInstance().healthCheck();

        return "main";
    }
}
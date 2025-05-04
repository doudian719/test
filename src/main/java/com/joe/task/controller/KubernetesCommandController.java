package com.joe.task.controller;

import com.joe.task.service.k8s.CommandService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kubernetes")
@RequiredArgsConstructor
public class KubernetesCommandController {

    private final CommandService commandService;

    @PostMapping("/execute-command")
    public String executeCommand(@RequestBody CommandRequest request) {
        return commandService.executeCommand(request.getEnv(), request.getCommand());
    }

    @Data
    public static class CommandRequest {
        private String env;
        private String command;
    }
} 
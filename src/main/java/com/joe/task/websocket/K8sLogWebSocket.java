package com.joe.task.websocket;

import com.joe.task.config.KubernetesClientManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint("/ws/k8s/log")
public class K8sLogWebSocket {
    private static KubernetesClientManager clientManager;

    @Autowired
    public void setClientManager(KubernetesClientManager clientManager) {
        K8sLogWebSocket.clientManager = clientManager;
    }

    private Session session;
    private LogWatch logWatch;
    private Thread logThread;
    private static final Map<String, K8sLogWebSocket> clients = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        clients.put(session.getId(), this);
        log.info("K8sLogWebSocket connected: {}", session.getId());

        Map<String, String> params = session.getRequestParameterMap()
                .entrySet()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(0)
                ));
        String env = params.get("env");
        String namespace = params.get("namespace");
        String pod = params.get("pod");
        String container = params.get("container");

        if (env == null || namespace == null || pod == null || container == null) {
            sendMessage("[error] Missing required parameters");
            closeSession();
            return;
        }

        try {
            KubernetesClient client = clientManager.getClient(env);
            // Only get new logs from now (do not send historical logs)
            logWatch = client.pods()
                    .inNamespace(namespace)
                    .withName(pod)
                    .inContainer(container)
                    .sinceTime(Instant.now().toString())
                    .watchLog();
            BufferedReader reader = new BufferedReader(new InputStreamReader(logWatch.getOutput()));
            logThread = new Thread(() -> {
                String line;
                try {
                    while ((line = reader.readLine()) != null && session.isOpen()) {
                        sendMessage(line);
                    }
                } catch (IOException e) {
                    log.error("Error reading log stream", e);
                }
            });
            logThread.start();
        } catch (Exception e) {
            log.error("Failed to start log stream", e);
            sendMessage("[error] Failed to start log stream: " + e.getMessage());
            closeSession();
        }
    }

    @OnClose
    public void onClose() {
        clients.remove(session.getId());
        if (logWatch != null) {
            logWatch.close();
        }
        if (logThread != null && logThread.isAlive()) {
            logThread.interrupt();
        }
        log.info("K8sLogWebSocket closed: {}", session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error for session {}", session.getId(), error);
        onClose();
    }

    @OnMessage
    public void onMessage(String message) {
        // 可扩展：处理前端发来的消息
    }

    private void sendMessage(String message) {
        try {
            if (session.isOpen()) {
                session.getBasicRemote().sendText(message);
            }
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
    }

    private void closeSession() {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            log.error("Error closing session", e);
        }
    }
} 
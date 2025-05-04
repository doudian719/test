package com.joe.task.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joe.task.service.k8s.EventService;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint("/kubernetes/events/watch")
public class K8sEventsWebSocket {

    private static EventService eventService;

    @Autowired
    public void setEventService(EventService eventService) {
        K8sEventsWebSocket.eventService = eventService;
    }

    private Session session;
    private Watch watch;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, K8sEventsWebSocket> clients = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        clients.put(session.getId(), this);
        log.info("New WebSocket connection established. Session ID: {}", session.getId());

        // Get query parameters
        Map<String, String> params = session.getRequestParameterMap()
                .entrySet()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(0)
                ));

        String env = params.get("env");
        String namespace = params.get("namespace");
        String type = params.get("type");

        try {
            // Start watching events
            watch = eventService.watchEvents(env, namespace, new Watcher<Event>() {
                @Override
                public void eventReceived(Action action, Event event) {
                    try {
                        // Convert event to a simplified format
                        Map<String, Object> eventData = new java.util.HashMap<>();
                        eventData.put("type", event.getType());
                        eventData.put("reason", event.getReason());
                        eventData.put("message", event.getMessage());
                        eventData.put("involvedObject", event.getInvolvedObject().getKind() + "/" + event.getInvolvedObject().getName());
                        eventData.put("lastTimestamp", event.getLastTimestamp());
                        eventData.put("count", event.getCount());

                        // Filter by type if specified
                        if (type != null && !type.isEmpty() && !type.equals(event.getType())) {
                            return;
                        }

                        // Send event to client
                        sendMessage(objectMapper.writeValueAsString(eventData));
                    } catch (Exception e) {
                        log.error("Error processing event", e);
                    }
                }

                @Override
                public void onClose(WatcherException e) {
                    log.error("Watch closed", e);
                }
            });
        } catch (Exception e) {
            log.error("Error starting watch", e);
            try {
                session.close();
            } catch (IOException ex) {
                log.error("Error closing session", ex);
            }
        }
    }

    @OnClose
    public void onClose() {
        clients.remove(session.getId());
        if (watch != null) {
            watch.close();
        }
        log.info("WebSocket connection closed. Session ID: {}", session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error for session " + session.getId(), error);
        clients.remove(session.getId());
        if (watch != null) {
            watch.close();
        }
    }

    @OnMessage
    public void onMessage(String message) {
        // Handle incoming messages if needed
    }

    private void sendMessage(String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
    }
} 
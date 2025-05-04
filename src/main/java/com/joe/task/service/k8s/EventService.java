package com.joe.task.service.k8s;

import com.joe.task.config.KubernetesClientManager;
import com.joe.task.dto.ContainerResourceInfo;
import com.joe.task.dto.EventInfo;
import com.joe.task.dto.PodInfo;
import com.joe.task.entity.PageBean;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EventService extends BaseService
{
    private final KubernetesClientManager clientManager;
    
    // Store events by environment and namespace
    private final Map<String, Map<String, Map<String, EventInfo>>> eventCache = new ConcurrentHashMap<>();
    
    // Track active watches by environment and namespace
    private final Map<String, Map<String, Watch>> activeWatches = new ConcurrentHashMap<>();
    
    // Default time window for events (2 hours)
    private static final Duration DEFAULT_EVENT_WINDOW = Duration.ofHours(2);
    
    // Flag to indicate if full pull is in progress
    private final AtomicBoolean fullPullInProgress = new AtomicBoolean(false);

    public EventService(KubernetesClientManager clientManager)
    {
        super(clientManager);
        this.clientManager = clientManager;
    }

    public List<PodInfo> getPodsInNamespace(String env, String namespace, Optional<String> status, Optional<String> name) {
        KubernetesClient client = clientManager.getClient(env);
        log.debug("Fetching pods in namespace: {} for environment: {}", namespace, env);
        try {
            List<Pod> pods = client.pods()
                    .inNamespace(namespace)
                    .list()
                    .getItems();

            List<PodInfo> podInfoList = pods.stream()
                    .map(this::convertToPodInfo)
                    .filter(podInfo -> !status.isPresent() || status.map(s -> s.equalsIgnoreCase(podInfo.getStatus())).orElse(true))
                    .filter(podInfo -> !name.isPresent() || name.map(s -> StringUtils.containsIgnoreCase(podInfo.getName(), name.get())).orElse(true))
                    .sorted(Comparator.comparing(PodInfo::getStartTimeMillis, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());

            return podInfoList;
        } catch (Exception e) {
            log.error("Error fetching pods in namespace: {} for environment: {}", namespace, env, e);
            throw new RuntimeException("Failed to fetch pods", e);
        }
    }

    private PodInfo convertToPodInfo(Pod pod) {
        PodInfo podInfo = new PodInfo();
        podInfo.setName(pod.getMetadata().getName());
        podInfo.setNamespace(pod.getMetadata().getNamespace());
        podInfo.setStatus(pod.getStatus().getPhase());
        podInfo.setIp(pod.getStatus().getPodIP());
        podInfo.setNodeName(pod.getSpec().getNodeName());
        podInfo.setCreationTimestamp(pod.getMetadata().getCreationTimestamp());

        // Calculate restart count
        int restartCount = pod.getStatus().getContainerStatuses()
                .stream()
                .mapToInt(ContainerStatus::getRestartCount)
                .sum();
        podInfo.setRestartCount(restartCount);

        // Calculate uptime and startTimeMillis
        String startTimeStr = pod.getStatus().getStartTime();
        if (startTimeStr != null) {
            Instant instant = Instant.parse(startTimeStr);
            LocalDateTime startTime = LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());

            // Set the startTimeMillis for sorting
            podInfo.setStartTimeMillis(instant.toEpochMilli());

            // Calculate and set the human-readable uptime
            java.time.Duration uptime = java.time.Duration.between(startTime, LocalDateTime.now());
            podInfo.setUptime(formatDuration(uptime));
        }

        // Get container resources
        List<ContainerResourceInfo> containers = pod.getSpec().getContainers()
                .stream()
                .map(container -> new ContainerResourceInfo(
                        container.getName(),
                        container.getResources().getRequests(),
                        container.getResources().getLimits()))
                .collect(Collectors.toList());
        podInfo.setContainers(containers);

        return podInfo;
    }

    public PageBean<EventInfo> getEventsInNamespace(String environment,
                                                    String namespace,
                                                    Optional<String> type,
                                                    Integer pageNo,
                                                    Integer pageSize) {
        KubernetesClient client = clientManager.getClient(environment);
        log.info("Fetching events for environment: {}, namespace: {}, type: {}, page: {}, size: {}", 
            environment, namespace, type.orElse("All"), pageNo, pageSize);
        
        // 获取所有事件并过滤
        List<EventInfo> allEvents = client.v1().events().inNamespace(namespace).list().getItems().stream()
            .map(event -> {
                // Convert Kubernetes event to EventInfo
                EventInfo eventInfo = new EventInfo();
                eventInfo.setName(event.getMetadata().getName());
                eventInfo.setNamespace(event.getMetadata().getNamespace());
                eventInfo.setType(event.getType());
                eventInfo.setReason(event.getReason());
                eventInfo.setMessage(event.getMessage());
                eventInfo.setInvolvedObject(event.getInvolvedObject().getKind());
                eventInfo.setCreationTimestamp(event.getMetadata().getCreationTimestamp());
                eventInfo.setLastTimestamp(event.getLastTimestamp() != null ? 
                    event.getLastTimestamp() : 
                    event.getMetadata().getCreationTimestamp());
                eventInfo.setCount(event.getCount());
                return eventInfo;
            })
            .filter(event -> type.map(t -> StringUtils.equalsIgnoreCase(event.getType(), t)).orElse(true))
            .sorted(Comparator.comparing(
                EventInfo::getLastTimestamp, 
                Comparator.nullsLast(Comparator.reverseOrder())
            ))
            .collect(Collectors.toList());

        // 计算分页
        int start = (pageNo - 1) * pageSize;
        int end = Math.min(start + pageSize, allEvents.size());
        
        // 获取当前页的数据
        List<EventInfo> pagedEvents = allEvents.subList(start, end);
        
        log.info("Found {} total events, returning {} events for page {}", 
            allEvents.size(), pagedEvents.size(), pageNo);
        
        // 返回分页对象
        return new PageBean<>(pagedEvents, (long) allEvents.size());
    }
    
    /**
     * Start watching events in the specified namespace with time filtering
     * 
     * @param environment The K8s environment
     * @param namespace The namespace to watch
     * @param eventHandler Consumer that will handle incoming events
     * @param timeWindow Optional duration to limit events (defaults to 2 hours)
     * @return true if watch was started successfully
     */
    public boolean startWatchingEvents(
            String environment, 
            String namespace, 
            Consumer<EventInfo> eventHandler,
            Optional<Duration> timeWindow) {
        
        KubernetesClient client = clientManager.getClient(environment);
        log.info("Starting watch for events in environment: {}, namespace: {}", environment, namespace);
        
        // Initialize cache structure if needed
        eventCache.computeIfAbsent(environment, k -> new ConcurrentHashMap<>())
                  .computeIfAbsent(namespace, k -> new ConcurrentHashMap<>());
                  
        // If there's already an active watch, close it first
        stopWatchingEvents(environment, namespace);
        
        try {
            // Calculate time filter (default 2 hours ago)
            Instant filterTime = Instant.now().minus(timeWindow.orElse(DEFAULT_EVENT_WINDOW));
            String fieldSelector = "lastTimestamp>=" + DateTimeFormatter.ISO_INSTANT.format(filterTime);
            
            ListOptions options = new ListOptionsBuilder()
                    .withFieldSelector(fieldSelector)
                    .build();
            
            // Create the watcher
            Watch watch = client.v1().events().inNamespace(namespace).withResourceVersion("0").watch(new Watcher<Event>() {
                @Override
                public void eventReceived(Action action, Event event) {
                    EventInfo eventInfo = convertToEventInfo(event);
                    
                    // Store in cache
                    eventCache.get(environment)
                             .get(namespace)
                             .put(eventInfo.getName(), eventInfo);
                             
                    // Notify handler
                    eventHandler.accept(eventInfo);
                    
                    log.debug("Event received: {} - {} - {}", action, event.getMetadata().getName(), event.getMessage());
                }

                @Override
                public void onClose(WatcherException e) {
                    if (e != null) {
                        log.warn("Watch closed with exception for environment: {}, namespace: {}: {}", 
                            environment, namespace, e.getMessage());
                        
                        // Remove from active watches
                        if (activeWatches.containsKey(environment)) {
                            activeWatches.get(environment).remove(namespace);
                        }
                        
                        // Try to reconnect after a short delay
                        new Thread(() -> {
                            try {
                                Thread.sleep(5000); // Wait 5 seconds before reconnecting
                                if (!Thread.currentThread().isInterrupted()) {
                                    startWatchingEvents(environment, namespace, eventHandler, timeWindow);
                                }
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }).start();
                    } else {
                        log.info("Watch closed normally for environment: {}, namespace: {}", environment, namespace);
                    }
                }
            });
            
            // Store active watch
            activeWatches.computeIfAbsent(environment, k -> new ConcurrentHashMap<>())
                         .put(namespace, watch);
            
            // Run immediate full pull to populate cache
            pullAllEvents(environment, namespace, timeWindow);
            
            return true;
        } catch (Exception e) {
            log.error("Failed to start watching events for environment: {}, namespace: {}", environment, namespace, e);
            return false;
        }
    }
    
    /**
     * Stop watching events for the specified environment and namespace
     * 
     * @param environment The K8s environment
     * @param namespace The namespace
     */
    public void stopWatchingEvents(String environment, String namespace) {
        if (activeWatches.containsKey(environment) && activeWatches.get(environment).containsKey(namespace)) {
            Watch watch = activeWatches.get(environment).get(namespace);
            try {
                watch.close();
                log.info("Stopped watching events for environment: {}, namespace: {}", environment, namespace);
            } catch (Exception e) {
                log.warn("Error closing watch for environment: {}, namespace: {}", environment, namespace, e);
            } finally {
                activeWatches.get(environment).remove(namespace);
            }
        }
    }
    
    /**
     * Pull all events for the specified environment and namespace to fill gaps
     * This is scheduled to run every 5 minutes, but can also be triggered manually
     * 
     * @param environment The K8s environment
     * @param namespace The namespace
     * @param timeWindow Optional time window to limit events
     */
    public void pullAllEvents(String environment, String namespace, Optional<Duration> timeWindow) {
        // Skip if another full pull is already in progress
        if (!fullPullInProgress.compareAndSet(false, true)) {
            log.debug("Skipping full event pull as another is already in progress");
            return;
        }
        
        try {
            KubernetesClient client = clientManager.getClient(environment);
            log.info("Performing full event pull for environment: {}, namespace: {}", environment, namespace);
            
            // Calculate time filter (default 2 hours ago)
            Instant filterTime = Instant.now().minus(timeWindow.orElse(DEFAULT_EVENT_WINDOW));
            
            // Initialize cache structure if needed
            Map<String, EventInfo> namespaceCache = eventCache
                .computeIfAbsent(environment, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(namespace, k -> new ConcurrentHashMap<>());
            
            // Get all events in the time window
            List<Event> events = client.v1().events().inNamespace(namespace).list().getItems().stream()
                .filter(event -> {
                    String timestamp = event.getLastTimestamp() != null ? 
                        event.getLastTimestamp() : 
                        event.getMetadata().getCreationTimestamp();
                    
                    if (timestamp == null) {
                        return false;
                    }
                    
                    Instant eventTime = Instant.parse(timestamp);
                    return eventTime.isAfter(filterTime);
                })
                .collect(Collectors.toList());
            
            // Update cache with all events
            for (Event event : events) {
                EventInfo eventInfo = convertToEventInfo(event);
                namespaceCache.put(eventInfo.getName(), eventInfo);
            }
            
            log.info("Completed full event pull for environment: {}, namespace: {}. Retrieved {} events", 
                environment, namespace, events.size());
                
        } catch (Exception e) {
            log.error("Error during full event pull for environment: {}, namespace: {}", environment, namespace, e);
        } finally {
            fullPullInProgress.set(false);
        }
    }
    
    /**
     * Scheduled task to pull all events every 5 minutes for all active watches
     * This helps fill any gaps from watch disruptions
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void scheduledEventPull() {
        log.debug("Running scheduled event pull for all active watches");
        
        for (Map.Entry<String, Map<String, Watch>> envEntry : activeWatches.entrySet()) {
            String environment = envEntry.getKey();
            
            for (String namespace : envEntry.getValue().keySet()) {
                try {
                    pullAllEvents(environment, namespace, Optional.of(DEFAULT_EVENT_WINDOW));
                } catch (Exception e) {
                    log.error("Error in scheduled event pull for environment: {}, namespace: {}", 
                        environment, namespace, e);
                }
            }
        }
    }
    
    /**
     * Get events from the cache for the specified environment and namespace
     * 
     * @param environment The K8s environment
     * @param namespace The namespace
     * @param type Optional event type filter
     * @param pageNo Page number
     * @param pageSize Page size
     * @return PageBean containing the requested events
     */
    public PageBean<EventInfo> getCachedEvents(
            String environment,
            String namespace, 
            Optional<String> type,
            Integer pageNo,
            Integer pageSize) {
        
        // Check if we have a cache for this environment and namespace
        if (!eventCache.containsKey(environment) || 
            !eventCache.get(environment).containsKey(namespace)) {
            log.warn("No event cache found for environment: {}, namespace: {}", environment, namespace);
            return new PageBean<>(List.of(), 0L);
        }
        
        // Get cached events and apply filters
        List<EventInfo> allEvents = eventCache.get(environment)
            .get(namespace)
            .values()
            .stream()
            .filter(event -> type.map(t -> StringUtils.equalsIgnoreCase(event.getType(), t)).orElse(true))
            .sorted(Comparator.comparing(
                EventInfo::getLastTimestamp, 
                Comparator.nullsLast(Comparator.reverseOrder())
            ))
            .collect(Collectors.toList());
        
        // Calculate pagination
        int start = (pageNo - 1) * pageSize;
        int end = Math.min(start + pageSize, allEvents.size());
        
        if (start >= allEvents.size()) {
            return new PageBean<>(List.of(), (long) allEvents.size());
        }
        
        // Get current page data
        List<EventInfo> pagedEvents = allEvents.subList(start, end);
        
        return new PageBean<>(pagedEvents, (long) allEvents.size());
    }
    
    /**
     * Convert a Kubernetes Event to EventInfo
     */
    private EventInfo convertToEventInfo(Event event) {
        EventInfo eventInfo = new EventInfo();
        eventInfo.setName(event.getMetadata().getName());
        eventInfo.setNamespace(event.getMetadata().getNamespace());
        eventInfo.setType(event.getType());
        eventInfo.setReason(event.getReason());
        eventInfo.setMessage(event.getMessage());
        eventInfo.setInvolvedObject(event.getInvolvedObject().getKind());
        eventInfo.setCreationTimestamp(event.getMetadata().getCreationTimestamp());
        eventInfo.setLastTimestamp(event.getLastTimestamp() != null ? 
            event.getLastTimestamp() : 
            event.getMetadata().getCreationTimestamp());
        eventInfo.setCount(event.getCount());
        return eventInfo;
    }

    public Watch watchEvents(String env, String namespace, Watcher<Event> watcher) {
        KubernetesClient client = clientManager.getClient(env);
        return client.v1().events().inNamespace(namespace).watch(watcher);
    }
}

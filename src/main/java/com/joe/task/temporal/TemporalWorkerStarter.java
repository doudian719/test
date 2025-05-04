package com.joe.task.temporal;

import com.joe.task.config.TemporalConfig;
import com.joe.task.temporal.activity.ServiceVerificationActivitiesImpl;
import com.joe.task.temporal.workflow.ServiceVerificationWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TemporalWorkerStarter {

    @Autowired
    private WorkerFactory workerFactory;
    
    @Autowired
    private ServiceVerificationActivitiesImpl activities;

//    @EventListener(ApplicationReadyEvent.class)
    public void startWorkers() {
        // Create worker
        Worker worker = workerFactory.newWorker(TemporalConfig.TASK_QUEUE);
        
        // Register workflow implementation
        worker.registerWorkflowImplementationTypes(ServiceVerificationWorkflowImpl.class);
        
        // Register activity implementation
        worker.registerActivitiesImplementations(activities);
        
        // Start all workers associated with the worker factory
        workerFactory.start();
    }
} 
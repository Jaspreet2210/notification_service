package com.example.notification.queue;

import com.example.notification.service.NotificationService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class NotificationProcessor implements CommandLineRunner {

    private final NotificationQueue queue;
    private final NotificationService service;

    // Concurrent worker pool to pull from the queue and process notifications in parallel
    private final ExecutorService workerPool = Executors.newFixedThreadPool(4);
    private volatile boolean running = true;

    @Autowired
    public NotificationProcessor(NotificationQueue queue, NotificationService service) {
        this.queue = queue;
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println("[Queue Processor] Initializing 4 concurrent notification consumer worker threads...");
        
        for (int i = 0; i < 4; i++) {
            final int workerId = i + 1;
            workerPool.submit(() -> {
                System.out.println("[Queue Processor] Worker thread #" + workerId + " started.");
                while (running) {
                    try {
                        // Dequeue blocks if the queue is empty
                        Long id = queue.dequeue();
                        
                        // Process the notification asynchronously
                        service.processAsync(id);
                        
                    } catch (InterruptedException e) {
                        System.out.println("[Queue Processor] Worker thread #" + workerId + " interrupted. Shutting down.");
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("[Queue Processor] Critical error in worker thread #" + workerId + ": " + e.getMessage());
                    }
                }
            });
        }
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("[Queue Processor] Shutting down worker thread pool...");
        running = false;
        workerPool.shutdownNow();
    }
}

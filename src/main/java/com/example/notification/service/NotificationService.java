package com.example.notification.service;

import com.example.notification.exception.TransientProviderException;
import com.example.notification.factory.NotificationStrategyFactory;
import com.example.notification.model.Notification;
import com.example.notification.model.NotificationChannel;
import com.example.notification.model.NotificationStatus;
import com.example.notification.observer.NotificationStatusChangedEvent;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.strategy.NotificationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.*;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationStrategyFactory strategyFactory;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${notification.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${notification.retry.initial-delay-ms:1000}")
    private long initialDelayMs;

    @Value("${notification.retry.multiplier:2.0}")
    private double retryMultiplier;

    // Dedicated executor pool to run external delivery tasks with timeouts
    private final ExecutorService deliveryExecutor = Executors.newCachedThreadPool();

    @Autowired
    public NotificationService(NotificationRepository repository,
                               NotificationStrategyFactory strategyFactory,
                               ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.strategyFactory = strategyFactory;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Retrieves a notification by ID.
     */
    public Optional<Notification> getNotification(Long id) {
        return repository.findById(id);
    }

    /**
     * Retrieves all notifications from the database.
     */
    public java.util.List<Notification> getAllNotifications() {
        return repository.findAll();
    }


    /**
     * Persists a new notification request in PENDING state (Transactional Outbox).
     */
    @Transactional
    public Notification createNotification(NotificationChannel channel, String recipient, String title, String content, String idempotencyKey) {
        Notification notification = new Notification(channel, recipient, title, content, idempotencyKey);
        // Initially scheduled immediately
        notification.setNextRetryAt(LocalDateTime.now());
        Notification saved = repository.save(notification);
        
        // Publish initial event
        publishStatusEvent(saved);
        
        return saved;
    }

    /**
     * Updates notification status and publishes the Observer event.
     */
    @Transactional
    public Notification updateStatus(Long id, NotificationStatus status, String lastError) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
        
        notification.setStatus(status);
        if (lastError != null) {
            notification.setLastError(lastError);
        }
        
        Notification saved = repository.save(notification);
        publishStatusEvent(saved);
        return saved;
    }

    /**
     * Safely executes delivery with timeout protection and schedules DB-backed retries upon failure.
     * Note: The execution itself runs outside a broad DB transaction to avoid connection holding!
     */
    public void processAsync(Long id) {
        // 1. Move state to PROCESSING inside a short transaction
        Notification notification;
        try {
            notification = updateStatus(id, NotificationStatus.PROCESSING, null);
        } catch (Exception e) {
            System.err.println("Could not transition notification " + id + " to PROCESSING: " + e.getMessage());
            return;
        }

        System.out.println("[Processor] Starting delivery of Notification: " + notification);

        // 2. Resolve Strategy
        NotificationStrategy strategy = strategyFactory.getStrategy(notification.getChannel());
        
        // 3. Execute strategy with timeout limits (e.g. 2 seconds connect/read limit)
        Future<?> future = deliveryExecutor.submit(() -> strategy.execute(notification));
        
        try {
            // Block at most 2 seconds for third-party mock integration response
            future.get(2, TimeUnit.SECONDS);

            // Success! Transition state to SUCCESS inside a short transaction
            markSuccess(id);
            System.out.println("[Processor] Notification " + id + " delivered successfully!");

        } catch (TimeoutException e) {
            future.cancel(true);
            handleFailure(id, new TransientProviderException("Adapter request timed out after 2000ms."));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            handleFailure(id, cause != null ? cause : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleFailure(id, e);
        }
    }

    @Transactional
    protected void markSuccess(Long id) {
        repository.findById(id).ifPresent(notification -> {
            notification.setStatus(NotificationStatus.SUCCESS);
            notification.setAttempts(notification.getAttempts() + 1);
            notification.setLastError(null);
            notification.setNextRetryAt(null);
            Notification saved = repository.save(notification);
            publishStatusEvent(saved);
        });
    }

    @Transactional
    protected void handleFailure(Long id, Throwable exception) {
        repository.findById(id).ifPresent(notification -> {
            int attempt = notification.getAttempts() + 1;
            notification.setAttempts(attempt);
            notification.setLastError(exception.getMessage());

            boolean isTransient = exception instanceof TransientProviderException || exception.getCause() instanceof TransientProviderException;

            if (isTransient && attempt < maxAttempts) {
                // Calculate exponential backoff: delay = initialDelay * (multiplier ^ (attempt - 1))
                long backoffMs = (long) (initialDelayMs * Math.pow(retryMultiplier, attempt - 1));
                notification.setStatus(NotificationStatus.PENDING); // Return to outbox for next cycle
                notification.setNextRetryAt(LocalDateTime.now().plusNanos(backoffMs * 1_000_000));
                
                System.out.println("[Retry Engine] Temporary failure for notification " + id + 
                        " (Attempt " + attempt + "/" + maxAttempts + "). Scheduling retry in " + backoffMs + "ms.");
            } else {
                // Hard failure or exceeded retries
                notification.setStatus(NotificationStatus.FAILED);
                notification.setNextRetryAt(null);
                System.out.println("[Retry Engine] Hard failure for notification " + id + 
                        " (Attempt " + attempt + "/" + maxAttempts + "). Error: " + exception.getMessage());
            }

            Notification saved = repository.save(notification);
            publishStatusEvent(saved);
        });
    }

    /**
     * Publishes status change events to the Spring Application context.
     */
    private void publishStatusEvent(Notification notification) {
        try {
            // Decouple event publishing
            eventPublisher.publishEvent(new NotificationStatusChangedEvent(this, notification));
        } catch (Exception e) {
            System.err.println("Failed to publish status changed event: " + e.getMessage());
        }
    }
}

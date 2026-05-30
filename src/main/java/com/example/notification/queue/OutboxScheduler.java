package com.example.notification.queue;

import com.example.notification.model.Notification;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxScheduler {

    private final NotificationRepository repository;
    private final NotificationQueue queue;

    @Autowired
    public OutboxScheduler(NotificationRepository repository, NotificationQueue queue) {
        this.repository = repository;
        this.queue = queue;
    }

    /**
     * Polls the outbox table for due messages every 1000ms.
     * Transitions them to QUEUED in a short transaction, then pushes them to the in-memory buffer.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void pollAndEnqueueDueNotifications() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Fetch PENDING notifications whose scheduled retry time has arrived or passed
        List<Notification> dueNotifications = repository.findByStatusAndNextRetryAtBefore(
                NotificationStatus.PENDING,
                now
        );

        if (!dueNotifications.isEmpty()) {
            System.out.println("[Outbox Scheduler] Found " + dueNotifications.size() + " notifications due for delivery.");
            for (Notification notification : dueNotifications) {
                // Change state to QUEUED in DB to prevent other instances/schedulers from double-processing
                notification.setStatus(NotificationStatus.QUEUED);
                repository.save(notification);

                // Push to in-memory processing queue
                queue.enqueue(notification.getId());
                System.out.println("[Outbox Scheduler] Enqueued notification ID: " + notification.getId());
            }
        }

        // 2. Self-Healing Crash Recovery: Check for any notification stuck in QUEUED or PROCESSING for > 30 seconds
        // (This happens if the server crashes unexpectedly during delivery execution).
        recoverStuckNotifications();
    }

    private void recoverStuckNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(30);

        // Fetch stuck QUEUED notifications
        List<Notification> stuckQueued = repository.findByStatus(NotificationStatus.QUEUED);
        for (Notification n : stuckQueued) {
            if (n.getUpdatedAt().isBefore(cutoff)) {
                System.out.println("[Self-Healing] Found stuck QUEUED notification ID: " + n.getId() + ". Reverting to PENDING.");
                n.setStatus(NotificationStatus.PENDING);
                n.setNextRetryAt(LocalDateTime.now()); // Re-run immediately
                repository.save(n);
            }
        }

        // Fetch stuck PROCESSING notifications
        List<Notification> stuckProcessing = repository.findByStatus(NotificationStatus.PROCESSING);
        for (Notification n : stuckProcessing) {
            if (n.getUpdatedAt().isBefore(cutoff)) {
                System.out.println("[Self-Healing] Found stuck PROCESSING notification ID: " + n.getId() + ". Reverting to PENDING.");
                n.setStatus(NotificationStatus.PENDING);
                n.setNextRetryAt(LocalDateTime.now()); // Re-run immediately
                repository.save(n);
            }
        }
    }
}

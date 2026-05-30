package com.example.notification.queue;

import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class NotificationQueue {

    // Thread-safe buffer storing IDs of notifications to be processed
    private final BlockingQueue<Long> queue = new LinkedBlockingQueue<>();

    /**
     * Pushes a notification ID into the queue.
     *
     * @param notificationId The database ID of the notification
     */
    public void enqueue(Long notificationId) {
        if (notificationId != null) {
            queue.offer(notificationId);
        }
    }

    /**
     * Polls the next notification ID from the queue. Blocks if the queue is empty.
     *
     * @return The notification ID
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public Long dequeue() throws InterruptedException {
        return queue.take();
    }

    /**
     * Gets the current size of the queue.
     *
     * @return The number of elements in the queue
     */
    public int size() {
        return queue.size();
    }
}

package com.example.notification.strategy;

import com.example.notification.model.Notification;
import com.example.notification.model.NotificationChannel;

public interface NotificationStrategy {
    
    /**
     * Executes the channel-specific delivery logic.
     *
     * @param notification The notification entity to deliver
     */
    void execute(Notification notification);

    /**
     * Gets the notification channel type supported by this strategy.
     *
     * @return The NotificationChannel enum value
     */
    NotificationChannel getChannel();
}

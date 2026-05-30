package com.example.notification.strategy;

import com.example.notification.adapter.NotificationAdapter;
import com.example.notification.model.Notification;
import com.example.notification.model.NotificationChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationStrategy implements NotificationStrategy {

    private final NotificationAdapter inAppAdapter;

    @Autowired
    public InAppNotificationStrategy(@Qualifier("inAppProviderAdapter") NotificationAdapter inAppAdapter) {
        this.inAppAdapter = inAppAdapter;
    }

    @Override
    public void execute(Notification notification) {
        // App-specific validations or data payload formatting can go here
        inAppAdapter.deliver(
                notification.getRecipient(),
                notification.getTitle(),
                notification.getContent()
        );
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.IN_APP;
    }
}

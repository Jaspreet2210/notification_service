package com.example.notification.strategy;

import com.example.notification.adapter.NotificationAdapter;
import com.example.notification.model.Notification;
import com.example.notification.model.NotificationChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationStrategy implements NotificationStrategy {

    private final NotificationAdapter emailAdapter;

    @Autowired
    public EmailNotificationStrategy(@Qualifier("emailProviderAdapter") NotificationAdapter emailAdapter) {
        this.emailAdapter = emailAdapter;
    }

    @Override
    public void execute(Notification notification) {
        // Pre-processing or routing specific to email can happen here
        emailAdapter.deliver(
                notification.getRecipient(),
                notification.getTitle(),
                notification.getContent()
        );
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }
}

package com.example.notification.strategy;

import com.example.notification.adapter.NotificationAdapter;
import com.example.notification.model.Notification;
import com.example.notification.model.NotificationChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class SmsNotificationStrategy implements NotificationStrategy {

    private final NotificationAdapter smsAdapter;

    @Autowired
    public SmsNotificationStrategy(@Qualifier("smsProviderAdapter") NotificationAdapter smsAdapter) {
        this.smsAdapter = smsAdapter;
    }

    @Override
    public void execute(Notification notification) {
        // SMS character limit validation or mobile format checks can go here
        smsAdapter.deliver(
                notification.getRecipient(),
                notification.getTitle(),
                notification.getContent()
        );
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.SMS;
    }
}

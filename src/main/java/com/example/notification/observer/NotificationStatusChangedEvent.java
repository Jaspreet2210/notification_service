package com.example.notification.observer;

import com.example.notification.model.Notification;
import org.springframework.context.ApplicationEvent;

public class NotificationStatusChangedEvent extends ApplicationEvent {

    private final Notification notification;

    public NotificationStatusChangedEvent(Object source, Notification notification) {
        super(source);
        // Store a copy or reference to the current state of the notification
        this.notification = notification;
    }

    public Notification getNotification() {
        return notification;
    }
}

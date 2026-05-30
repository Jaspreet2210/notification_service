package com.example.notification.adapter;

public interface NotificationAdapter {
    /**
     * Unified interface to deliver a notification.
     * Adapts third-party clients to a single, standardized signature.
     *
     * @param recipient The recipient (email address, phone number, or user ID)
     * @param title     The notification title
     * @param content   The notification content
     */
    void deliver(String recipient, String title, String content);
}

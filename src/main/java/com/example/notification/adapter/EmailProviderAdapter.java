package com.example.notification.adapter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("emailProviderAdapter")
public class EmailProviderAdapter implements NotificationAdapter {

    private final MockSendGridEmailClient sendGridClient;

    @Autowired
    public EmailProviderAdapter(MockSendGridEmailClient sendGridClient) {
        this.sendGridClient = sendGridClient;
    }

    @Override
    public void deliver(String recipient, String title, String content) {
        // Adapts unified deliver interface to the SendGrid client's custom method signature
        sendGridClient.sendEmailMessage(recipient, title, content);
    }
}

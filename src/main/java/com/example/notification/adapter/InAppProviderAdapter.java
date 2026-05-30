package com.example.notification.adapter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("inAppProviderAdapter")
public class InAppProviderAdapter implements NotificationAdapter {

    private final MockFirebasePushClient firebaseClient;

    @Autowired
    public InAppProviderAdapter(MockFirebasePushClient firebaseClient) {
        this.firebaseClient = firebaseClient;
    }

    @Override
    public void deliver(String recipient, String title, String content) {
        // Adapts unified deliver interface to FCM push token delivery
        firebaseClient.pushToUserDevice(recipient, title, content);
    }
}

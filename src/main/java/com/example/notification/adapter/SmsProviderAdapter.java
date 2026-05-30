package com.example.notification.adapter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("smsProviderAdapter")
public class SmsProviderAdapter implements NotificationAdapter {

    private final MockTwilioSmsClient twilioClient;

    @Autowired
    public SmsProviderAdapter(MockTwilioSmsClient twilioClient) {
        this.twilioClient = twilioClient;
    }

    @Override
    public void deliver(String recipient, String title, String content) {
        // Adapts unified deliver interface to the Twilio client's deliverSMS method signature
        // Formats title and content together for SMS standard
        String smsText = "[" + title + "] " + content;
        twilioClient.deliverSMS(recipient, smsText);
    }
}

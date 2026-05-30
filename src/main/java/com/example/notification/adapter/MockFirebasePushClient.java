package com.example.notification.adapter;

import com.example.notification.exception.TransientProviderException;
import com.example.notification.service.SimulationConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class MockFirebasePushClient {

    private final SimulationConfigService configService;
    private final Random random = new Random();

    @Autowired
    public MockFirebasePushClient(SimulationConfigService configService) {
        this.configService = configService;
    }

    public void pushToUserDevice(String userToken, String title, String alertBody) {
        simulateNetworkLatency();

        if (configService.isSimulateOutage()) {
            throw new TransientProviderException("Firebase FCM Provider is completely down (Simulated Outage).");
        }

        // Simulate transient socket exception
        if (random.nextDouble() < configService.getFailureRate()) {
            throw new TransientProviderException("FCM API Server connection reset by peer. socket error.");
        }

        System.out.println("[Firebase Mock] In-App Notification pushed to " + userToken + " | Title: " + title);
    }

    private void simulateNetworkLatency() {
        int min = configService.getMinLatencyMs();
        int max = configService.getMaxLatencyMs();
        int latency = min + random.nextInt(max - min + 1);
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

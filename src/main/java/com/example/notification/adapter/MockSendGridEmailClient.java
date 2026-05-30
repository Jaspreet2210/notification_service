package com.example.notification.adapter;

import com.example.notification.exception.TransientProviderException;
import com.example.notification.service.SimulationConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class MockSendGridEmailClient {

    private final SimulationConfigService configService;
    private final Random random = new Random();

    @Autowired
    public MockSendGridEmailClient(SimulationConfigService configService) {
        this.configService = configService;
    }

    public void sendEmailMessage(String toAddress, String subject, String htmlBody) {
        simulateNetworkLatency();

        if (configService.isSimulateOutage()) {
            throw new TransientProviderException("Email Provider (SendGrid) is completely down (Simulated Outage).");
        }

        // Simulate random transient network error
        if (random.nextDouble() < configService.getFailureRate()) {
            throw new TransientProviderException("Transient error communicating with SendGrid SMTP server. Connection timed out.");
        }

        System.out.println("[SendGrid Mock] Email successfully sent to " + toAddress + " | Subject: " + subject);
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

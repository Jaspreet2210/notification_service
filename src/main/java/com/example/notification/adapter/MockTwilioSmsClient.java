package com.example.notification.adapter;

import com.example.notification.exception.TransientProviderException;
import com.example.notification.service.SimulationConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class MockTwilioSmsClient {

    private final SimulationConfigService configService;
    private final Random random = new Random();

    @Autowired
    public MockTwilioSmsClient(SimulationConfigService configService) {
        this.configService = configService;
    }

    public void deliverSMS(String phoneNumber, String smsText) {
        simulateNetworkLatency();

        if (configService.isSimulateOutage()) {
            throw new TransientProviderException("SMS Provider (Twilio) is completely down (Simulated Outage).");
        }

        // Simulate random transient cellular network error
        if (random.nextDouble() < configService.getFailureRate()) {
            throw new TransientProviderException("Twilio API Gateway failure: 504 Gateway Timeout.");
        }

        System.out.println("[Twilio Mock] SMS successfully sent to " + phoneNumber + " | Content: " + smsText);
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

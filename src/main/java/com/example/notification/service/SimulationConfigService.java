package com.example.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SimulationConfigService {

    @Value("${notification.provider.failure-rate:0.3}")
    private double failureRate;

    @Value("${notification.provider.min-latency-ms:300}")
    private int minLatencyMs;

    @Value("${notification.provider.max-latency-ms:1000}")
    private int maxLatencyMs;

    private volatile boolean simulateOutage = false;

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public int getMinLatencyMs() {
        return minLatencyMs;
    }

    public void setMinLatencyMs(int minLatencyMs) {
        this.minLatencyMs = minLatencyMs;
    }

    public int getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(int maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }

    public boolean isSimulateOutage() {
        return simulateOutage;
    }

    public void setSimulateOutage(boolean simulateOutage) {
        this.simulateOutage = simulateOutage;
    }
}

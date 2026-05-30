package com.example.notification.service;

import com.example.notification.model.IdempotentRequest;
import com.example.notification.repository.IdempotentRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotentRequestRepository repository;

    @Autowired
    public IdempotencyService(IdempotentRequestRepository repository) {
        this.repository = repository;
    }

    /**
     * Checks if a request with the given idempotency key is already in progress or completed.
     * If not, registers it as "PROCESSING".
     *
     * @param key The unique idempotency key
     * @return Optional containing the existing request if found, or empty if it's new
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Optional<IdempotentRequest> getOrRegister(String key) {
        if (key == null || key.trim().isEmpty()) {
            return Optional.empty();
        }

        Optional<IdempotentRequest> existing = repository.findById(key);
        if (existing.isPresent()) {
            return existing;
        }

        // Register new key as PROCESSING
        IdempotentRequest newRequest = new IdempotentRequest(key, "PROCESSING");
        repository.save(newRequest);
        return Optional.empty();
    }

    /**
     * Completes an idempotent request by caching its API response details.
     *
     * @param key        The idempotency key
     * @param statusCode The HTTP status code to cache
     * @param body       The JSON response body payload to cache
     */
    @Transactional
    public void complete(String key, int statusCode, String body) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }

        repository.findById(key).ifPresent(req -> {
            req.setStatus("COMPLETED");
            req.setResponseStatusCode(statusCode);
            req.setResponseBody(body);
            repository.save(req);
        });
    }
}

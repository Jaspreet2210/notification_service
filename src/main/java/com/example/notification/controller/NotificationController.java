package com.example.notification.controller;

import com.example.notification.dto.NotificationRequestDto;
import com.example.notification.model.IdempotentRequest;
import com.example.notification.model.Notification;
import com.example.notification.model.NotificationChannel;
import com.example.notification.observer.SseNotificationObserver;
import com.example.notification.queue.NotificationQueue;
import com.example.notification.service.IdempotencyService;
import com.example.notification.service.NotificationService;
import com.example.notification.service.SimulationConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/notifications")
@CrossOrigin(origins = "*") // Allow dashboard to run locally/across origins easily
public class NotificationController {

    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final SimulationConfigService simulationService;
    private final SseNotificationObserver sseObserver;
    private final NotificationQueue queue;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotificationController(NotificationService notificationService,
                                  IdempotencyService idempotencyService,
                                  SimulationConfigService simulationService,
                                  SseNotificationObserver sseObserver,
                                  NotificationQueue queue,
                                  ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.idempotencyService = idempotencyService;
        this.simulationService = simulationService;
        this.sseObserver = sseObserver;
        this.queue = queue;
        this.objectMapper = objectMapper;
    }

    /**
     * Send a notification with idempotency guarantees.
     */
    @PostMapping
    public ResponseEntity<?> sendNotification(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody NotificationRequestDto requestDto) {

        // 1. Enforce idempotency key requirement
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("timestamp", LocalDateTime.now());
            error.put("status", HttpStatus.BAD_REQUEST.value());
            error.put("error", "Bad Request");
            error.put("message", "Header 'Idempotency-Key' is required to prevent duplicate delivery.");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            // 2. Query or Register the Idempotency Key
            Optional<IdempotentRequest> idempotentCheck = idempotencyService.getOrRegister(idempotencyKey);
            
            if (idempotentCheck.isPresent()) {
                IdempotentRequest existingReq = idempotentCheck.get();
                
                if ("PROCESSING".equals(existingReq.getStatus())) {
                    // Conflict: The exact same request is currently being dispatched
                    Map<String, Object> conflict = new HashMap<>();
                    conflict.put("timestamp", LocalDateTime.now());
                    conflict.put("status", HttpStatus.CONFLICT.value());
                    conflict.put("error", "Conflict");
                    conflict.put("message", "A request with this Idempotency-Key is already being processed.");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(conflict);
                } else {
                    // Completed: Return the cached response body and status code!
                    Object cachedBody = objectMapper.readValue(existingReq.getResponseBody(), Object.class);
                    return ResponseEntity.status(existingReq.getResponseStatusCode())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(cachedBody);
                }
            }

            // 3. Translate channel string to strong Enum type
            NotificationChannel channel;
            try {
                channel = NotificationChannel.valueOf(requestDto.getChannel().toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                // If invalid, free the key and throw exception
                idempotencyService.complete(idempotencyKey, HttpStatus.BAD_REQUEST.value(), "{\"error\": \"Invalid Channel\"}");
                throw new IllegalArgumentException("Unsupported channel: " + requestDto.getChannel() + ". Supported channels: EMAIL, SMS, IN_APP");
            }

            // 4. Create database record (Status: PENDING) inside short transaction
            Notification saved = notificationService.createNotification(
                    channel,
                    requestDto.getRecipient(),
                    requestDto.getTitle(),
                    requestDto.getContent(),
                    idempotencyKey
            );

            // 5. Serialize successfully created notification to JSON
            String jsonResponse = objectMapper.writeValueAsString(saved);

            // 6. Complete Idempotency key tracking
            idempotencyService.complete(idempotencyKey, HttpStatus.CREATED.value(), jsonResponse);

            // 7. Enqueue in-memory for zero-delay async processing (Safe since DB commit is completed)
            queue.enqueue(saved.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(saved);

        } catch (Exception e) {
            // Register error on idempotency key to prevent lockout
            idempotencyService.complete(idempotencyKey, HttpStatus.INTERNAL_SERVER_ERROR.value(), "{\"error\": \"" + e.getMessage() + "\"}");
            throw new RuntimeException("Failed to process notification: " + e.getMessage(), e);
        }
    }

    /**
     * Get details of a single notification.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Notification> getNotification(@PathVariable Long id) {
        return notificationService.getNotification(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all notifications (useful for dashboard list loading).
     */
    @GetMapping
    public ResponseEntity<List<Notification>> getAllNotifications() {
        return ResponseEntity.ok(notificationService.getAllNotifications());
    }

    /**
     * Open Server-Sent Events real-time stream.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUpdates() {
        return sseObserver.registerEmitter();
    }

    /**
     * Control outage simulation parameter.
     */
    @PostMapping("/simulate-outage")
    public ResponseEntity<Map<String, Object>> toggleOutage(@RequestBody Map<String, Boolean> payload) {
        Boolean simulate = payload.getOrDefault("simulate", false);
        simulationService.setSimulateOutage(simulate);

        Map<String, Object> response = new HashMap<>();
        response.put("simulateOutage", simulate);
        response.put("message", simulate 
                ? "Simulated 100% provider outage activated. All attempts will fail and trigger exponential backoff."
                : "Simulated outage deactivated. Provider communication restored."
        );
        return ResponseEntity.ok(response);
    }
}

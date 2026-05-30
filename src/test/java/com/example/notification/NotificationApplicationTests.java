package com.example.notification;

import com.example.notification.factory.NotificationStrategyFactory;
import com.example.notification.model.IdempotentRequest;
import com.example.notification.model.Notification;
import com.example.notification.model.NotificationChannel;
import com.example.notification.model.NotificationStatus;
import com.example.notification.service.IdempotencyService;
import com.example.notification.service.NotificationService;
import com.example.notification.strategy.EmailNotificationStrategy;
import com.example.notification.strategy.NotificationStrategy;
import com.example.notification.strategy.SmsNotificationStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NotificationApplicationTests {

    @Autowired
    private NotificationStrategyFactory strategyFactory;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private NotificationService notificationService;

    @Test
    void contextLoads() {
        // Verifies that the Spring Boot context initializes correctly
        assertNotNull(strategyFactory);
        assertNotNull(idempotencyService);
        assertNotNull(notificationService);
    }

    @Test
    void testStrategyFactoryResolvesCorrectly() {
        // Strategy Pattern verification: Factory must return proper channel strategies
        NotificationStrategy emailStrategy = strategyFactory.getStrategy(NotificationChannel.EMAIL);
        assertNotNull(emailStrategy);
        assertTrue(emailStrategy instanceof EmailNotificationStrategy);

        NotificationStrategy smsStrategy = strategyFactory.getStrategy(NotificationChannel.SMS);
        assertNotNull(smsStrategy);
        assertTrue(smsStrategy instanceof SmsNotificationStrategy);
    }

    @Test
    void testIdempotencyEngineDetectsDuplicates() {
        String testKey = "junit-test-idempotency-" + System.currentTimeMillis();

        // First registration check: must return Optional.empty() (indicating key is brand new)
        Optional<IdempotentRequest> firstCheck = idempotencyService.getOrRegister(testKey);
        assertTrue(firstCheck.isEmpty(), "First check should register the key as processing and return empty");

        // Second registration check under the same key: must return the active IdempotentRequest
        Optional<IdempotentRequest> secondCheck = idempotencyService.getOrRegister(testKey);
        assertTrue(secondCheck.isPresent(), "Second check should detect duplicate and return registered entity");
        assertEquals("PROCESSING", secondCheck.get().getStatus(), "Duplicate key should be marked as PROCESSING");

        // Complete the request and check again
        idempotencyService.complete(testKey, 201, "{\"status\": \"CREATED\"}");
        Optional<IdempotentRequest> thirdCheck = idempotencyService.getOrRegister(testKey);
        assertTrue(thirdCheck.isPresent());
        assertEquals("COMPLETED", thirdCheck.get().getStatus());
        assertEquals(201, thirdCheck.get().getResponseStatusCode());
        assertEquals("{\"status\": \"CREATED\"}", thirdCheck.get().getResponseBody());
    }

    @Test
    void testNotificationCreationInPendingState() {
        String idempotencyKey = "junit-test-outbox-" + System.currentTimeMillis();
        
        // Transactional Outbox Pattern: Notification must be saved as PENDING
        Notification notification = notificationService.createNotification(
                NotificationChannel.EMAIL,
                "test@example.com",
                "JUnit Title",
                "JUnit Message Content",
                idempotencyKey
        );

        assertNotNull(notification.getId());
        assertEquals(NotificationStatus.PENDING, notification.getStatus());
        assertEquals(0, notification.getAttempts());
        assertNotNull(notification.getNextRetryAt());
    }
}

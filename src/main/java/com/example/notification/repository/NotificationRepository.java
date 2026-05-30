package com.example.notification.repository;

import com.example.notification.model.Notification;
import com.example.notification.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    Optional<Notification> findByIdempotencyKey(String idempotencyKey);
    
    List<Notification> findByStatus(NotificationStatus status);
    
    // Find pending notifications or those due for a retry
    List<Notification> findByStatusAndNextRetryAtBefore(NotificationStatus status, LocalDateTime dateTime);
}

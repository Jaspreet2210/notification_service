package com.example.notification.repository;

import com.example.notification.model.IdempotentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotentRequestRepository extends JpaRepository<IdempotentRequest, String> {
}

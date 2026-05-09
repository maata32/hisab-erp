package com.minierp.pos.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {
    List<CashMovement> findBySessionIdOrderByOccurredAtAsc(UUID sessionId);
}

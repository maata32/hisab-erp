package com.minierp.pos.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByIdempotencyKey(String idempotencyKey);

    Page<Sale> findBySessionIdOrderByCompletedAtDesc(UUID sessionId, Pageable pageable);

    Page<Sale> findByRegisterIdAndCompletedAtBetween(UUID registerId, Instant from, Instant to, Pageable pageable);
}

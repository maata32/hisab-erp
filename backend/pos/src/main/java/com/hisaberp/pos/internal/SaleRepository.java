package com.hisaberp.pos.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByIdempotencyKey(String idempotencyKey);

    Page<Sale> findBySessionIdOrderByCompletedAtDesc(UUID sessionId, Pageable pageable);

    Page<Sale> findByRegisterIdAndCompletedAtBetween(UUID registerId, Instant from, Instant to, Pageable pageable);

    @Query("SELECT COALESCE(SUM(s.total), 0) FROM Sale s WHERE s.completedAt >= :from AND s.completedAt < :to")
    BigDecimal sumTotalBetween(@Param("from") Instant from, @Param("to") Instant to);
}

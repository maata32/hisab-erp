package com.minierp.pos.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

    @Query("SELECT s FROM CashSession s WHERE s.registerId = :reg AND s.status = 'OPEN'")
    Optional<CashSession> findOpen(@Param("reg") UUID registerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CashSession s WHERE s.id = :id")
    Optional<CashSession> lockById(@Param("id") UUID id);
}

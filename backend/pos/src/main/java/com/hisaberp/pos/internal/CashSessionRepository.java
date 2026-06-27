package com.hisaberp.pos.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

    @Query("SELECT s FROM CashSession s WHERE s.registerId = :reg AND s.status = 'OPEN'")
    Optional<CashSession> findOpen(@Param("reg") UUID registerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CashSession s WHERE s.id = :id")
    Optional<CashSession> lockById(@Param("id") UUID id);

    @Query("SELECT s FROM CashSession s WHERE s.status = 'CLOSED' ORDER BY s.closedAt ASC")
    List<CashSession> findPendingValidation();

    @Query("SELECT s FROM CashSession s WHERE s.cashierUserId = :cashier AND s.status = 'CLOSED' ORDER BY s.closedAt DESC")
    List<CashSession> findPendingByCashier(@Param("cashier") UUID cashierId);

    @Query("SELECT s FROM CashSession s WHERE s.cashierUserId = :cashier AND s.status = 'VALIDATED' " +
           "AND s.validatedAt >= :from AND s.validatedAt < :to ORDER BY s.validatedAt DESC")
    List<CashSession> findValidatedByCashierBetween(@Param("cashier") UUID cashierId,
                                                    @Param("from") Instant from,
                                                    @Param("to") Instant to);
}

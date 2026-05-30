package com.minierp.allocation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.UUID;

interface AllocationRepository extends JpaRepository<Allocation, UUID> {

    /**
     * Sum of allocated amounts where the given (type, id) is on the positive
     * side. Used to compute the remaining open balance of a positive item
     * (e.g. an unconsumed customer payment) on top of the legacy
     * {@code payment_allocations} table.
     */
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM Allocation a " +
           "WHERE a.positiveType = :type AND a.positiveId = :id")
    BigDecimal sumByPositive(String type, UUID id);

    /**
     * Symmetric sum on the negative side. Used for invoices and refund-out
     * type items.
     */
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM Allocation a " +
           "WHERE a.negativeType = :type AND a.negativeId = :id")
    BigDecimal sumByNegative(String type, UUID id);
}

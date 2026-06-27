package com.hisaberp.allocation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface AllocationRepository extends JpaRepository<Allocation, UUID> {

    /**
     * Sum of allocated amounts where the given (type, id) is on the positive
     * side. Used to compute the remaining open balance of a positive item
     * (e.g. an unconsumed customer payment) on top of the legacy
     * {@code payment_allocations} table. Reversed rows (soft-void) are excluded.
     */
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM Allocation a " +
           "WHERE a.positiveType = :type AND a.positiveId = :id AND a.reversedAt IS NULL")
    BigDecimal sumByPositive(String type, UUID id);

    /**
     * Symmetric sum on the negative side. Used for invoices and refund-out
     * type items. Reversed rows (soft-void) are excluded.
     */
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM Allocation a " +
           "WHERE a.negativeType = :type AND a.negativeId = :id AND a.reversedAt IS NULL")
    BigDecimal sumByNegative(String type, UUID id);

    /**
     * Still-active (not yet reversed) allocation rows where the given payment
     * sits on the positive side. Used by {@code RefundAllocationReversalListener}
     * to soft-void the rows a refunded payment produced.
     */
    @Query("SELECT a FROM Allocation a " +
           "WHERE a.positiveId = :positiveId AND a.positiveType IN :types AND a.reversedAt IS NULL")
    List<Allocation> findActiveByPositive(UUID positiveId, Collection<String> types);

    /**
     * Still-active rows where the given (type, id) sits on the negative side and
     * the positive side is one of {@code positiveTypes}. Used by
     * {@code InvoicePaymentsDetachedListener} to locate the PAYMENT → INVOICE
     * rows it must soft-void when an avoir detaches an invoice from its payments.
     */
    @Query("SELECT a FROM Allocation a " +
           "WHERE a.negativeType = :negativeType AND a.negativeId = :negativeId " +
           "AND a.positiveType IN :positiveTypes AND a.reversedAt IS NULL")
    List<Allocation> findActiveByNegative(String negativeType, UUID negativeId, Collection<String> positiveTypes);

    /**
     * Symmetric to {@link #findActiveByNegative}: still-active rows where the
     * given (type, id) sits on the POSITIVE side and the negative side is one of
     * {@code negativeTypes}. Used by the sales avoir-detach (a sale invoice is
     * POSITIVE in the net-position model, its payments NEGATIVE).
     */
    @Query("SELECT a FROM Allocation a " +
           "WHERE a.positiveType = :positiveType AND a.positiveId = :positiveId " +
           "AND a.negativeType IN :negativeTypes AND a.reversedAt IS NULL")
    List<Allocation> findActiveByPositiveSide(String positiveType, UUID positiveId, Collection<String> negativeTypes);
}

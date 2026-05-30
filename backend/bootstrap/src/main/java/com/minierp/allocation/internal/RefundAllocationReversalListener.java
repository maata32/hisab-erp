package com.minierp.allocation.internal;

import com.minierp.payment.api.PaymentRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Counterpart to {@link PaymentAllocationListener}: when a payment is refunded
 * ({@link PaymentRefundedEvent}), soft-void the unified {@code allocations} rows
 * the original confirm mirrored, so they stop counting toward open balances and
 * stop claiming the (now un-paid) invoices were settled by this payment.
 *
 * <p>Runs in the publisher's transaction ({@code Propagation.MANDATORY}); a
 * failure rolls back the refund with it. Only positive-side rows produced by the
 * payment itself are reversed — credit-consumption rows are left untouched (see
 * {@link PaymentRefundedEvent}). The load+mutate path (rather than a bulk JPQL
 * update) keeps the {@code updated_at}/{@code version} audit columns correct;
 * the row count per refund is small.</p>
 */
@Component
@RequiredArgsConstructor
class RefundAllocationReversalListener {

    private static final List<String> POSITIVE_TYPES =
            List.of(AllocationEngineImpl.T_PAYMENT, AllocationEngineImpl.T_SUPPLIER_PAYMENT);

    private final AllocationRepository allocations;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(PaymentRefundedEvent event) {
        List<Allocation> rows = allocations.findActiveByPositive(
                event.originalPaymentId(), POSITIVE_TYPES);
        if (rows.isEmpty()) return;

        Instant now = Instant.now();
        String reason = "Refund " + event.refundNumber()
                + (event.reason() != null && !event.reason().isBlank() ? " — " + event.reason().trim() : "");
        for (Allocation a : rows) {
            a.setReversedAt(now);
            a.setReversedBy(event.userId());
            a.setReversalReason(reason);
        }
        allocations.saveAll(rows);
    }
}

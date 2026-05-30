package com.minierp.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Published synchronously at the end of {@code PaymentService.confirm}. Carries
 * a snapshot of the payment header + every {@code payment_allocations} row that
 * was applied. Listeners run in the same transaction so a failure rolls back
 * the confirm itself.
 *
 * <p>Phase 5 use case: the AllocationEngine listens for this event and writes
 * each invoice-side allocation into the unified {@code allocations} audit
 * table. Non-invoice targets ({@code CUSTOMER_BALANCE}, {@code CUSTOMER_CREDIT},
 * {@code EXPENSE}, {@code SALARY}, {@code SALE}) are ignored by the engine —
 * they create a different kind of book-keeping that does not fit the
 * positive↔negative allocation contract.</p>
 *
 * @param paymentId      id of the just-confirmed payment row
 * @param paymentType    one of {@code CUSTOMER_PAYMENT, CUSTOMER_DEPOSIT,
 *                       CUSTOMER_REFUND, CUSTOMER_CREDIT_WITHDRAWAL,
 *                       SUPPLIER_PAYMENT, SUPPLIER_REFUND}
 * @param partyId        the counterparty
 * @param allocations    per-target slice, snapshotted from the legacy
 *                       {@code payment_allocations} table
 */
public record PaymentConfirmedEvent(
        UUID paymentId,
        String paymentType,
        UUID partyId,
        List<AllocationLine> allocations) {

    public record AllocationLine(String targetType, UUID targetId, BigDecimal amount) {}
}

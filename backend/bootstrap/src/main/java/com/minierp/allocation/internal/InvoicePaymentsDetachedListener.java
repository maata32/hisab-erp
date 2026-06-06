package com.minierp.allocation.internal;

import com.minierp.partner.api.CustomerCreditOperations;
import com.minierp.sales.api.InvoicePaymentsDetachedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Reacts to {@link InvoicePaymentsDetachedEvent} (fired when a total avoir
 * detaches an invoice from its payments). For every still-active
 * {@code PAYMENT → INVOICE} row of the invoice it:
 * <ol>
 *   <li>soft-voids the row — the payment no longer settles the invoice, the
 *       avoir does;</li>
 *   <li>mints an {@code OVERPAYMENT} customer credit for the freed amount,
 *       stamped with the originating payment id so the AllocationEngine subtracts
 *       it from that payment's open residual ({@code source_payment_id}) and
 *       never double-counts the freed cash.</li>
 * </ol>
 * Runs in the publisher's transaction ({@code Propagation.MANDATORY}) so the
 * avoir creation and the detachment commit or roll back together.
 */
@Component
@RequiredArgsConstructor
class InvoicePaymentsDetachedListener {

    private final AllocationRepository allocations;
    private final CustomerCreditOperations customerCreditOps;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(InvoicePaymentsDetachedEvent event) {
        // Net-position model: a sale invoice is POSITIVE, its CASH_IN payments
        // NEGATIVE → the rows to soft-void are positive=INVOICE / negative=PAYMENT.
        List<Allocation> rows = allocations.findActiveByPositiveSide(
                AllocationEngineImpl.T_INVOICE, event.invoiceId(), List.of(AllocationEngineImpl.T_PAYMENT));
        Instant now = Instant.now();
        for (Allocation row : rows) {
            row.setReversedAt(now);
            row.setReversalReason("Détachée par avoir " + event.creditNoteNumber());
            customerCreditOps.grantCredit(
                    event.partyId(), row.getAmount(), "OVERPAYMENT",
                    "Avoir " + event.creditNoteNumber() + " — paiement détaché",
                    row.getNegativeId());
        }
    }
}

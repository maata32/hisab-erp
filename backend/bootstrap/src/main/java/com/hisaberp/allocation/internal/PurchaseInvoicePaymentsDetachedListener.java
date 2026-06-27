package com.hisaberp.allocation.internal;

import com.hisaberp.purchase.api.PurchaseInvoicePaymentsDetachedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Reacts to {@link PurchaseInvoicePaymentsDetachedEvent} (fired when a total
 * purchase avoir detaches an invoice from its supplier payments). For every
 * still-active {@code SUPPLIER_PAYMENT → PURCHASE_INVOICE} row it soft-voids the
 * row — the payment no longer settles the invoice, the avoir does.
 *
 * <p>Unlike the sales-side {@link InvoicePaymentsDetachedListener}, no credit is
 * minted: the supplier-payment residual reopens automatically (the engine's
 * {@code openSupplierPayments} query derives it from {@code amount − Σ positive
 * allocations}), so the freed cash becomes an available open item to offset
 * future supplier invoices or be refunded.</p>
 *
 * Runs in the publisher's transaction ({@code Propagation.MANDATORY}).
 */
@Component
@RequiredArgsConstructor
class PurchaseInvoicePaymentsDetachedListener {

    private final AllocationRepository allocations;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(PurchaseInvoicePaymentsDetachedEvent event) {
        List<Allocation> rows = allocations.findActiveByNegative(
                AllocationEngineImpl.T_PURCHASE_INVOICE, event.purchaseInvoiceId(),
                List.of(AllocationEngineImpl.T_PAYMENT));
        Instant now = Instant.now();
        for (Allocation row : rows) {
            row.setReversedAt(now);
            row.setReversalReason("Détachée par avoir " + event.creditNoteNumber());
        }
    }
}

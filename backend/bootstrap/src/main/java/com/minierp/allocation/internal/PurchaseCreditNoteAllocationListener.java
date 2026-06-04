package com.minierp.allocation.internal;

import com.minierp.purchase.api.PurchaseCreditNoteAppliedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirrors a {@code PURCHASE_CREDIT_NOTE → PURCHASE_INVOICE} application in the
 * unified {@code allocations} audit table. Mirror of
 * {@link CreditNoteAllocationListener}. Runs in the publisher's transaction
 * ({@code Propagation.MANDATORY}) so the avoir creation and the allocation row
 * commit or roll back together.
 */
@Component
@RequiredArgsConstructor
class PurchaseCreditNoteAllocationListener {

    private final AllocationRepository allocations;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(PurchaseCreditNoteAppliedEvent event) {
        if (event.imputedAmount() == null || event.imputedAmount().signum() <= 0) return;
        allocations.save(Allocation.builder()
                .partyId(event.partyId())
                .positiveType(AllocationEngineImpl.T_PURCHASE_CREDIT_NOTE)
                .positiveId(event.creditNoteId())
                .negativeType(AllocationEngineImpl.T_PURCHASE_INVOICE)
                .negativeId(event.purchaseInvoiceId())
                .amount(event.imputedAmount())
                .notes("Avoir d'achat " + event.creditNoteNumber())
                .build());
    }
}

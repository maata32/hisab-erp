package com.hisaberp.allocation.internal;

import com.hisaberp.sales.api.CreditNoteAppliedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 6 — mirrors a CREDIT_NOTE → INVOICE application in the unified
 * {@code allocations} audit table. Runs in the same transaction as the
 * publisher (Propagation.MANDATORY) so the credit-note creation and the
 * allocation row commit or roll back together.
 */
@Component
@RequiredArgsConstructor
class CreditNoteAllocationListener {

    private final AllocationRepository allocations;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(CreditNoteAppliedEvent event) {
        if (event.imputedAmount() == null || event.imputedAmount().signum() <= 0) return;
        // Net-position model: the sale invoice is POSITIVE (the customer owes us),
        // the sale credit note NEGATIVE (we owe the customer back).
        allocations.save(Allocation.builder()
                .partyId(event.partyId())
                .positiveType(AllocationEngineImpl.T_INVOICE)
                .positiveId(event.invoiceId())
                .negativeType(AllocationEngineImpl.T_CREDIT_NOTE)
                .negativeId(event.creditNoteId())
                .amount(event.imputedAmount())
                .notes("Credit note " + event.creditNoteNumber())
                .build());
    }
}

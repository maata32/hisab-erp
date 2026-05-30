package com.minierp.allocation.internal;

import com.minierp.sales.api.CreditNoteAppliedEvent;
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
        allocations.save(Allocation.builder()
                .partyId(event.partyId())
                .positiveType(AllocationEngineImpl.T_CREDIT_NOTE)
                .positiveId(event.creditNoteId())
                .negativeType(AllocationEngineImpl.T_INVOICE)
                .negativeId(event.invoiceId())
                .amount(event.imputedAmount())
                .notes("Credit note " + event.creditNoteNumber())
                .build());
    }
}

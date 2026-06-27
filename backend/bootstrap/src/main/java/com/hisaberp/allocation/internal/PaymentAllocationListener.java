package com.hisaberp.allocation.internal;

import com.hisaberp.payment.api.PaymentConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Phase 5 — when a payment confirm fires {@link PaymentConfirmedEvent}, mirror
 * each invoice-side allocation into the unified {@code allocations} table so
 * the engine becomes the single source of truth going forward. The listener
 * joins the publisher's transaction ({@code Propagation.MANDATORY}); a failure
 * rolls back the confirm with it.
 *
 * <p>Only invoice-shaped targets are mirrored: {@code SALE_INVOICE} and
 * {@code PURCHASE_INVOICE}. Other target types ({@code CUSTOMER_BALANCE},
 * {@code CUSTOMER_CREDIT}, {@code EXPENSE}, {@code SALARY}, {@code SALE}) carry
 * book-keeping that does not match the positive↔negative pairing — the surplus
 * row going to {@code CUSTOMER_CREDIT}, for instance, mints a credit but is
 * not itself an allocation between two open items.</p>
 */
@Component
@RequiredArgsConstructor
class PaymentAllocationListener {

    private final AllocationRepository allocations;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(PaymentConfirmedEvent event) {
        for (var line : event.allocations()) {
            // Net-position convention: a sale invoice is POSITIVE (the customer
            // owes us) settled by a CASH_IN payment (NEGATIVE); a purchase invoice
            // is NEGATIVE (we owe the supplier) settled by a CASH_OUT payment
            // (POSITIVE). Payments use the unified T_PAYMENT tag. Only
            // invoice-shaped targets are mirrored into the engine.
            String positiveType, negativeType;
            UUID positiveId, negativeId;
            switch (line.targetType()) {
                case "SALE_INVOICE" -> {
                    positiveType = AllocationEngineImpl.T_INVOICE;          positiveId = line.targetId();
                    negativeType = AllocationEngineImpl.T_PAYMENT;          negativeId = event.paymentId();
                }
                case "PURCHASE_INVOICE" -> {
                    positiveType = AllocationEngineImpl.T_PAYMENT;          positiveId = event.paymentId();
                    negativeType = AllocationEngineImpl.T_PURCHASE_INVOICE; negativeId = line.targetId();
                }
                default -> { continue; }
            }

            allocations.save(Allocation.builder()
                    .partyId(event.partyId())
                    .positiveType(positiveType)
                    .positiveId(positiveId)
                    .negativeType(negativeType)
                    .negativeId(negativeId)
                    .amount(line.amount())
                    .build());
        }
    }
}

package com.minierp.allocation.internal;

import com.minierp.payment.api.PaymentConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        String positiveType = positiveTypeFor(event.paymentType());
        if (positiveType == null) return;

        for (var line : event.allocations()) {
            String negativeType = switch (line.targetType()) {
                case "SALE_INVOICE" -> AllocationEngineImpl.T_INVOICE;
                case "PURCHASE_INVOICE" -> AllocationEngineImpl.T_PURCHASE_INVOICE;
                default -> null;
            };
            if (negativeType == null) continue;

            allocations.save(Allocation.builder()
                    .partyId(event.partyId())
                    .positiveType(positiveType)
                    .positiveId(event.paymentId())
                    .negativeType(negativeType)
                    .negativeId(line.targetId())
                    .amount(line.amount())
                    .build());
        }
    }

    /** Maps a {@code PaymentType} to the engine's positive-side source-type
     *  constant. Returns {@code null} for payment types that don't bring funds
     *  to the operator (refunds, withdrawals — those would sit on the negative
     *  side of an allocation, not the positive one). */
    private static String positiveTypeFor(String paymentType) {
        return switch (paymentType) {
            case "CUSTOMER_PAYMENT", "CUSTOMER_DEPOSIT" -> AllocationEngineImpl.T_PAYMENT;
            case "SUPPLIER_PAYMENT" -> AllocationEngineImpl.T_SUPPLIER_PAYMENT;
            default -> null;
        };
    }
}

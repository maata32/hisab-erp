package com.minierp.payment.api;

import java.util.UUID;

/**
 * Published synchronously at the end of {@code PaymentService.refund}, after the
 * original payment is marked {@code REFUNDED} and the cash-out refund document
 * is minted. Listeners run in the same transaction so a failure rolls back the
 * refund itself.
 *
 * <p>Closes the loop opened by {@link PaymentConfirmedEvent}: the confirm event
 * mirrors a payment's invoice allocations into the unified {@code allocations}
 * table; this event tells the AllocationEngine to soft-void those same rows when
 * the payment is refunded, so the audit table stops claiming the (now un-paid)
 * invoice was settled by this payment.</p>
 *
 * <p>Only the positive-side rows the payment itself produced are reversed
 * ({@code PAYMENT} / {@code SUPPLIER_PAYMENT} on the positive side). Credits the
 * payment minted are clawed back separately by {@code revokeCreditsBySourcePayment};
 * any credit that was already consumed against an invoice keeps its
 * {@code CUSTOMER_CREDIT → INVOICE} allocation row, because that settlement is
 * not undone by refunding the original payment.</p>
 *
 * @param originalPaymentId the payment being refunded (the positive side of the
 *                          rows to reverse)
 * @param paymentType       the original payment type — one of
 *                          {@code CUSTOMER_PAYMENT, CUSTOMER_DEPOSIT, SUPPLIER_PAYMENT}
 * @param partyId           the counterparty
 * @param refundPaymentId   the freshly minted refund document
 * @param refundNumber      the refund document number, embedded in the audit note
 * @param userId            the operator who triggered the refund (→ {@code reversed_by})
 * @param reason            optional free-text reason captured on the refund
 */
public record PaymentRefundedEvent(
        UUID originalPaymentId,
        String paymentType,
        UUID partyId,
        UUID refundPaymentId,
        String refundNumber,
        UUID userId,
        String reason) {}

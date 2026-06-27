package com.hisaberp.partner.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Programmatic creation of customer credits, used by the sales module when a
 * credit note exceeds the unpaid balance of the invoice it is issued against.
 */
public interface CustomerCreditOperations {

    /**
     * Grant an active credit to the customer.
     *
     * @param customerId the partner (customer) id
     * @param amount     positive credit amount
     * @param source     one of {@code DEPOSIT, OVERPAYMENT, REFUND, MANUAL_ADJUSTMENT}
     * @param notes      free-form note (origin number, etc.)
     * @return id of the new credit row
     */
    UUID grantCredit(UUID customerId, BigDecimal amount, String source, String notes);

    /**
     * Same as {@link #grantCredit(UUID, BigDecimal, String, String)} but also
     * stamps the new credit row with the originating payment id, so that a later
     * refund can locate and cancel the credits it gave birth to.
     */
    UUID grantCredit(UUID customerId, BigDecimal amount, String source, String notes, UUID sourcePaymentId);

    /**
     * Cancel every still-active credit that was issued from {@code sourcePaymentId}.
     * Returns the total amount that was actually clawed back (sum of the active
     * remaining_amount across affected rows). Credits already partly consumed
     * are revoked only on what is left — the consumed slice has already been
     * spent and cannot be unspent from here.
     */
    BigDecimal revokeCreditsBySourcePayment(UUID sourcePaymentId, String reason);

    /**
     * Sum of remaining amounts that would be clawed back by
     * {@link #revokeCreditsBySourcePayment} — used by the refund preview so
     * the UI can warn about the impact before mutating anything.
     */
    BigDecimal sumRevokableCreditsBySourcePayment(UUID sourcePaymentId);

    /**
     * Consume part of an active credit row in-place. Reduces
     * {@code remaining_amount} and marks the row {@code EXHAUSTED} when it
     * hits zero. The caller is responsible for the upstream audit trail
     * (e.g. allocation row pointing to the consumer) — this method does
     * not create a {@code customer_credit_usages} entry.
     *
     * @return the amount actually consumed (capped by {@code remaining_amount}).
     * @throws com.hisaberp.shared.error.BusinessException if the credit is
     *         missing or not in {@code ACTIVE} status.
     */
    BigDecimal consumeCredit(UUID creditId, BigDecimal amount, String notes);
}

package com.minierp.payment.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only projection of payments for the customer statement of account.
 * Returns CONFIRMED payments only (DRAFT and CANCELLED are excluded).
 */
public interface PaymentLookup {
    List<StatementPaymentEntry> findConfirmedForCustomer(
            UUID customerId, LocalDate from, LocalDate to);
}

package com.minierp.payment.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only projection of payments for the partner statement of account.
 * Returns CONFIRMED payments only (DRAFT and CANCELLED are excluded).
 */
public interface PaymentLookup {
    List<StatementPaymentEntry> findConfirmedForCustomer(
            UUID customerId, LocalDate from, LocalDate to);

    /** Confirmed supplier-side payments (cash out we paid the supplier) for the statement. */
    List<StatementPaymentEntry> findConfirmedForSupplier(
            UUID supplierId, LocalDate from, LocalDate to);
}

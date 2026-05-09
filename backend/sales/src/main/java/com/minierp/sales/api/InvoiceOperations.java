package com.minierp.sales.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Invoice mutation facade used by the payment module.
 */
public interface InvoiceOperations {
    Optional<InvoiceSummary> findById(UUID invoiceId);
    List<InvoiceSummary> findUnpaidByCustomer(UUID customerId);
    void applyPayment(UUID invoiceId, BigDecimal amount);
    void markOverdue(UUID invoiceId);
}

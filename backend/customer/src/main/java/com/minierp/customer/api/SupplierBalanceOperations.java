package com.minierp.customer.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Balance mutation facade used by the purchase and payment modules.
 */
public interface SupplierBalanceOperations {
    void addToInvoiced(UUID supplierId, BigDecimal amount);
    void addToPaid(UUID supplierId, BigDecimal amount, boolean isLastPaymentToday);
    BigDecimal getBalanceAmount(UUID supplierId);
}

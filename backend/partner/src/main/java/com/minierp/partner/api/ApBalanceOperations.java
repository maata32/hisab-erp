package com.minierp.partner.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Balance mutation facade used by the purchase and payment modules.
 */
public interface ApBalanceOperations {
    void addToInvoiced(UUID supplierId, BigDecimal amount);
    void addToPaid(UUID supplierId, BigDecimal amount, boolean isLastPaymentToday);
    BigDecimal getBalanceAmount(UUID supplierId);
}

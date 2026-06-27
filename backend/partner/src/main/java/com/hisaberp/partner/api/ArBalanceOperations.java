package com.hisaberp.partner.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Balance mutation facade used by the payment module.
 */
public interface ArBalanceOperations {
    void addToInvoiced(UUID customerId, BigDecimal amount);
    void addToPaid(UUID customerId, BigDecimal amount, boolean isLastPaymentToday);
    void addToOverdue(UUID customerId, BigDecimal amount);
    BigDecimal getBalanceAmount(UUID customerId);
}

package com.minierp.partner.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Balance mutation facade used by the purchase and payment modules.
 */
public interface ApBalanceOperations {
    void addToInvoiced(UUID supplierId, BigDecimal amount);
    void addToPaid(UUID supplierId, BigDecimal amount, boolean isLastPaymentToday);
    BigDecimal getBalanceAmount(UUID supplierId);

    /** Accounts-payable snapshot for the supplier side of the partner statement. */
    ApSnapshot getApSnapshot(UUID supplierId);

    record ApSnapshot(
            BigDecimal totalInvoiced,
            BigDecimal totalPaid,
            BigDecimal balance,
            LocalDate lastPaymentDate
    ) {}
}

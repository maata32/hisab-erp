package com.minierp.partner.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only data needed by the customer statement of account.
 */
public interface CustomerStatementLookup {

    /** Open credits (status ACTIVE) granted to the customer, optionally filtered by createdAt range. */
    List<StatementCreditEntry> findActiveCreditsForStatement(UUID customerId, LocalDate from, LocalDate to);

    /** Snapshot of the balance row for the customer. */
    BalanceSnapshot getBalance(UUID customerId);

    record BalanceSnapshot(
            BigDecimal totalInvoiced,
            BigDecimal totalPaid,
            BigDecimal balance,
            BigDecimal overdueAmount,
            LocalDate lastPaymentDate
    ) {}
}

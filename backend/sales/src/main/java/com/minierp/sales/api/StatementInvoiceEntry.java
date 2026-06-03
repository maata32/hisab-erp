package com.minierp.sales.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Invoice projection used by customer statements. The {@code lines} field is
 * populated only when the caller asks for a detailed statement; it is null
 * (or empty) for the summary statement to keep the payload small.
 */
public record StatementInvoiceEntry(
        UUID id,
        String number,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal balance,
        String status,
        List<StatementInvoiceLine> lines,
        Instant createdAt
) {}

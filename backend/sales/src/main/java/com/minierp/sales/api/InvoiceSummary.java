package com.minierp.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceSummary(
        UUID id,
        String number,
        UUID customerId,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal balance,
        String status
) {}

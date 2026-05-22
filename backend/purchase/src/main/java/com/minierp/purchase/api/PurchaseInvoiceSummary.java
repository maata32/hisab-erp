package com.minierp.purchase.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseInvoiceSummary(
        UUID id,
        String number,
        UUID supplierId,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal balance,
        String status
) {}

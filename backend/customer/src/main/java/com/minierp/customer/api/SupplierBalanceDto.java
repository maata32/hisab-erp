package com.minierp.customer.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SupplierBalanceDto(
        UUID supplierId,
        BigDecimal totalInvoiced,
        BigDecimal totalPaid,
        BigDecimal balance,
        LocalDate lastPaymentDate
) {}

package com.minierp.customer.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerBalanceDto(
        UUID customerId,
        BigDecimal totalInvoiced,
        BigDecimal totalPaid,
        BigDecimal balance,
        BigDecimal overdueAmount,
        LocalDate lastPaymentDate
) {}

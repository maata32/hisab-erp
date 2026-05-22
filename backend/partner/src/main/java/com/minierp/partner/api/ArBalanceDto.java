package com.minierp.partner.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ArBalanceDto(
        UUID partyId,
        BigDecimal totalInvoiced,
        BigDecimal totalPaid,
        BigDecimal balance,
        BigDecimal overdueAmount,
        LocalDate lastPaymentDate
) {}

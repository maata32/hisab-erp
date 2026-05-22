package com.minierp.partner.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ApBalanceDto(
        UUID partyId,
        BigDecimal totalInvoiced,
        BigDecimal totalPaid,
        BigDecimal balance,
        LocalDate lastPaymentDate
) {}

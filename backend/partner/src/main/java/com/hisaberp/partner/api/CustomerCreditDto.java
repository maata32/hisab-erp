package com.hisaberp.partner.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerCreditDto(
        UUID id,
        UUID customerId,
        BigDecimal initialAmount,
        BigDecimal remainingAmount,
        String source,
        LocalDate expiresAt,
        String status,
        String notes,
        Instant createdAt
) {}

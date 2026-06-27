package com.hisaberp.partner.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatementCreditEntry(
        UUID id,
        Instant createdAt,
        BigDecimal initialAmount,
        BigDecimal remainingAmount,
        String source,
        String status,
        String notes
) {}

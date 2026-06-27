package com.hisaberp.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StatementPaymentEntry(
        UUID id,
        String number,
        LocalDate paymentDate,
        BigDecimal amount,
        String method,
        String reference,
        String status,
        Instant createdAt
) {}

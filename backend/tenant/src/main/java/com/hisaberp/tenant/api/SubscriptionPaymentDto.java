package com.hisaberp.tenant.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A recorded subscription payment for a tenant (super-admin ledger). */
public record SubscriptionPaymentDto(
        UUID id,
        UUID organizationId,
        int years,
        int months,
        BigDecimal amount,
        String currency,
        LocalDate paidAt,
        Instant periodStart,
        Instant periodEnd,
        String attachmentUrl,
        boolean cancelled,
        Instant cancelledAt,
        Instant createdAt
) {}

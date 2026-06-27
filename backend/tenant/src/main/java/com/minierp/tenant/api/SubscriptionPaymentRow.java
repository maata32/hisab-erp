package com.minierp.tenant.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A subscription payment enriched with its organization + plan, for the global super-admin ledger. */
public record SubscriptionPaymentRow(
        UUID id,
        UUID organizationId,
        String organizationCode,
        String organizationName,
        String planCode,
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

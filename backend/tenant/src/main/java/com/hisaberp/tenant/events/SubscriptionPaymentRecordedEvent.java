package com.hisaberp.tenant.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a super-admin records a subscription payment for a tenant. Consumed by the
 * notifications module to e-mail the tenant a receipt (amount + covered period).
 */
public record SubscriptionPaymentRecordedEvent(
        UUID organizationId,
        String tenantCode,
        String organizationName,
        String recipientEmail,
        String recipientName,
        String locale,
        int years,
        int months,
        BigDecimal amount,
        String currency,
        Instant periodStart,
        Instant periodEnd,
        Instant occurredAt
) {}

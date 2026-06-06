package com.minierp.tenant.events;

import java.time.Instant;
import java.util.UUID;

/** Reminder that a tenant's trial is about to end in {@code daysLeft} days. */
public record TenantTrialExpiringEvent(
        UUID organizationId,
        String tenantCode,
        String organizationName,
        String recipientEmail,
        int daysLeft,
        String locale,
        Instant occurredAt) {}

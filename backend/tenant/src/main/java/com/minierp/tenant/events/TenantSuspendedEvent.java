package com.minierp.tenant.events;

import java.time.Instant;
import java.util.UUID;

/** A tenant was suspended — manually by a SUPER_ADMIN or automatically after the grace period. */
public record TenantSuspendedEvent(
        UUID organizationId,
        String tenantCode,
        String organizationName,
        String recipientEmail,
        String reason,
        String locale,
        Instant occurredAt) {}

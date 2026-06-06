package com.minierp.tenant.events;

import java.time.Instant;
import java.util.UUID;

/** A SUPER_ADMIN rejected a PENDING tenant; it moved to ARCHIVED. */
public record TenantRejectedEvent(
        UUID organizationId,
        String tenantCode,
        String organizationName,
        String recipientEmail,
        String recipientName,
        String reason,
        String locale,
        Instant occurredAt) {}

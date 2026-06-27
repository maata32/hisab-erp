package com.minierp.tenant.events;

import java.time.Instant;
import java.util.UUID;

/** Published on every tenant status transition. Carries recipient info so the notifications
 *  module can e-mail the tenant without querying tenant internals. */
public record OrganizationStatusChangedEvent(
        UUID organizationId,
        String tenantCode,
        String organizationName,
        String recipientEmail,
        String locale,
        String oldStatus,
        String newStatus,
        String reason,
        Instant occurredAt) {}

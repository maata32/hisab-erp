package com.minierp.tenant.events;

import java.time.Instant;
import java.util.UUID;

public record OrganizationStatusChangedEvent(
        UUID organizationId,
        String oldStatus,
        String newStatus,
        String reason,
        Instant occurredAt) {}

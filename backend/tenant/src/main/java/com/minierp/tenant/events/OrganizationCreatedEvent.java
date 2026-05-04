package com.minierp.tenant.events;

import java.time.Instant;
import java.util.UUID;

public record OrganizationCreatedEvent(UUID organizationId, String code, Instant occurredAt) {}

package com.minierp.tenant.events;

import java.time.Instant;
import java.util.UUID;

/**
 * A new tenant submitted a public self-service registration. The tenant sits in
 * PENDING until a SUPER_ADMIN approves it. Published from the identity module
 * (which owns the admin user created alongside the organization).
 */
public record TenantRegisteredEvent(
        UUID organizationId,
        String tenantCode,
        String organizationName,
        String recipientEmail,
        String recipientName,
        String locale,
        Instant occurredAt) {}

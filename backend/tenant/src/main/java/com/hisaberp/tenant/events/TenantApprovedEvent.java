package com.hisaberp.tenant.events;

import java.time.Instant;
import java.util.UUID;

/**
 * A SUPER_ADMIN approved a PENDING tenant; it moved to TRIAL. The identity module
 * listens to promote {@code adminUserId} to TENANT_ADMIN; the notifications module
 * listens to e-mail the tenant. {@code adminUserId} may be null for tenants created
 * directly by a super-admin (no registration admin user).
 */
public record TenantApprovedEvent(
        UUID organizationId,
        String tenantCode,
        String organizationName,
        UUID adminUserId,
        String recipientEmail,
        String recipientName,
        String locale,
        Instant occurredAt) {}

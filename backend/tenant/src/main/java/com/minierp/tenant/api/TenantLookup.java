package com.minierp.tenant.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read-only port for other modules (identity especially).
 * Implementations live inside the tenant.internal package.
 */
public interface TenantLookup {
    Optional<TenantSnapshot> findByCode(String code);
    Optional<TenantSnapshot> findById(UUID id);

    /** Returns the subscription plan limits for the tenant, or sensible defaults when no subscription exists. */
    PlanLimits findLimitsForTenant(UUID tenantId);

    /** Returns the tenant's branding info (name, address, phone, email, logo) for PDF rendering. */
    Optional<TenantBranding> findBrandingById(UUID id);

    /** Active subscription plans, for the public registration form. */
    List<PlanView> listActivePlans();
}

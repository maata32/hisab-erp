package com.hisaberp.tenant.api;

/**
 * Per-tenant subscription limits. A {@code null} field means "unlimited".
 * Other modules call {@link TenantLookup#findLimitsForTenant} to enforce quotas.
 */
public record PlanLimits(
        Integer maxUsers,
        Integer maxProducts,
        Integer maxCashRegisters,
        Integer maxProductImages) {

    /** Default applied when a tenant has no subscription row yet. */
    public static PlanLimits defaults() {
        return new PlanLimits(null, null, null, 5);
    }
}

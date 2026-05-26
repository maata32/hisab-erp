package com.minierp.tenant.api;

import java.util.UUID;

/**
 * Read-only port for other modules that need a small piece of tenant configuration
 * (e.g. how many fractional digits to display monetary amounts with).
 */
public interface TenantSettingsLookup {

    /** Currency decimal places for the given tenant. Returns 0 when unset / tenant not found. */
    int getCurrencyDecimalPlaces(UUID tenantId);

    /** Paper size for PDF rendering ("A4" or "A5"). Returns "A4" when unset / tenant not found. */
    String getPaperSize(UUID tenantId);
}

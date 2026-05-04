package com.minierp.tenant.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Public read-only port for other modules (identity especially).
 * Implementations live inside the tenant.internal package.
 */
public interface TenantLookup {
    Optional<TenantSnapshot> findByCode(String code);
    Optional<TenantSnapshot> findById(UUID id);
}

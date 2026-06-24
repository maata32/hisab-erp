package com.minierp.shared.persistence;

import com.minierp.shared.tenant.TenantContext;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Application-level tenant check for by-id loads.
 *
 * <p>Spring Data {@code findById} is a JPA {@code EntityManager.find} primary-key lookup,
 * which <strong>bypasses the Hibernate {@code tenantFilter}</strong> (the filter only applies
 * to queries). PostgreSQL RLS (defense layer 2) is the safety net, but it is defeated whenever
 * the application DB role carries {@code BYPASSRLS}/superuser. So every by-id load of a
 * tenant-aware entity that is reachable from a controller must verify the row belongs to the
 * current tenant, otherwise a caller from tenant A can read/modify tenant B's row.</p>
 */
public final class TenantGuard {

    private TenantGuard() {}

    /**
     * Returns {@code entity} when it belongs to the current tenant; otherwise throws the supplied
     * exception (typically a {@code NotFoundException}) so cross-tenant access is indistinguishable
     * from a missing row.
     */
    public static <T extends TenantAwareEntity> T requireSameTenant(
            T entity, Supplier<? extends RuntimeException> orElseThrow) {
        if (entity == null || !TenantContext.require().equals(entity.getTenantId())) {
            throw orElseThrow.get();
        }
        return entity;
    }

    /** Convenience overload for the {@code Optional} returned by {@code findById}. */
    public static <T extends TenantAwareEntity> T requireSameTenant(
            Optional<T> entity, Supplier<? extends RuntimeException> orElseThrow) {
        return requireSameTenant(entity.orElse(null), orElseThrow);
    }
}

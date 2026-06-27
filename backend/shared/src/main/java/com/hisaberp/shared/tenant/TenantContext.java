package com.hisaberp.shared.tenant;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static Optional<UUID> tryGet() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID require() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("Tenant context is not set on this thread");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}

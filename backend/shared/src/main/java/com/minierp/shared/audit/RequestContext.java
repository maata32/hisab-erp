package com.minierp.shared.audit;

import java.util.Optional;

/**
 * Carries IP / user-agent / trace-id from the HTTP layer down to services that emit AuditEvents.
 * The filter sets it on entry; the GlobalExceptionHandler clears it on exit.
 */
public final class RequestContext {

    private static final ThreadLocal<Holder> CURRENT = new ThreadLocal<>();

    private RequestContext() {}

    public record Holder(String ipAddress, String userAgent, String traceId) {}

    public static void set(String ipAddress, String userAgent, String traceId) {
        CURRENT.set(new Holder(ipAddress, userAgent, traceId));
    }

    public static Optional<Holder> tryGet() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}

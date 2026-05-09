package com.minierp.notifications.api;

/**
 * Outbound SMS contract. Implementations:
 *  - {@code chinguitel} provider (HTTP API, stub in V1 — see ADR-001)
 *  - {@code mauritel} provider (HTTP API, stub in V1)
 *  - {@code log} fallback that just logs the message in dev
 *
 * Selection is driven by {@code app.notifications.sms.default-provider} or per-call
 * {@code preferredProvider}. A failure on the chosen provider falls back to the next
 * in priority order; a synthesized {@link SmsResult} reflects which provider was used.
 */
public interface SmsSender {

    SmsResult send(String to, String body);

    SmsResult send(String to, String body, String preferredProvider);
}

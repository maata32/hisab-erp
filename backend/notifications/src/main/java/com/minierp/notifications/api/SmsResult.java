package com.minierp.notifications.api;

import java.time.Instant;

public record SmsResult(
        boolean accepted,
        String provider,
        String providerMessageId,
        String error,
        Instant sentAt) {

    public static SmsResult ok(String provider, String providerMessageId) {
        return new SmsResult(true, provider, providerMessageId, null, Instant.now());
    }

    public static SmsResult failed(String provider, String error) {
        return new SmsResult(false, provider, null, error, Instant.now());
    }
}

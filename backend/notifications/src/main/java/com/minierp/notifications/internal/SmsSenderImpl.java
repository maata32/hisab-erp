package com.minierp.notifications.internal;

import com.minierp.notifications.api.SmsResult;
import com.minierp.notifications.api.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * SMS sender wired with Chinguitel + Mauritel adapters and a {@code log} fallback.
 *
 * <p>The HTTP adapters are <em>stubbed</em> in V1 (per ADR-001): if the provider has no
 * configured {@code apiUrl}/{@code apiKey}, calls log a warning and return a synthetic
 * accepted result. When real credentials are set later, a {@link java.net.http.HttpClient}
 * call will be wired here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class SmsSenderImpl implements SmsSender {

    private final SmsProperties properties;

    @Override
    public SmsResult send(String to, String body) {
        return send(to, body, properties.defaultProvider());
    }

    @Override
    public SmsResult send(String to, String body, String preferredProvider) {
        List<String> order = orderFor(preferredProvider);
        SmsResult last = null;
        for (String provider : order) {
            last = adapter(provider).apply(new Message(to, body));
            if (last.accepted()) return last;
            log.warn("SMS provider {} rejected message to {}: {}", provider, to, last.error());
        }
        return last == null ? SmsResult.failed("none", "no_providers_configured") : last;
    }

    private List<String> orderFor(String preferred) {
        if (preferred == null || preferred.isBlank()) preferred = "log";
        var fallback = properties.fallbackOrder();
        if (fallback == null || fallback.isEmpty()) fallback = List.of("log");
        return java.util.stream.Stream
                .concat(java.util.stream.Stream.of(preferred.toLowerCase()),
                        fallback.stream().map(String::toLowerCase))
                .distinct()
                .toList();
    }

    private Function<Message, SmsResult> adapter(String provider) {
        return switch (provider) {
            case "chinguitel" -> m -> stubProvider("chinguitel", properties.chinguitel(), m);
            case "mauritel"   -> m -> stubProvider("mauritel",   properties.mauritel(),   m);
            case "log" -> m -> {
                log.info("[SMS-LOG] to={} body={}", m.to(), m.body());
                return SmsResult.ok("log", UUID.randomUUID().toString());
            };
            default -> m -> SmsResult.failed(provider, "unknown_provider");
        };
    }

    private SmsResult stubProvider(String name, SmsProperties.Provider cfg, Message m) {
        if (cfg == null || cfg.apiUrl() == null || cfg.apiUrl().isBlank()
                || cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            log.warn("[SMS-STUB:{}] no credentials configured — message logged only. to={} body={}",
                    name, m.to(), m.body());
            return SmsResult.ok(name + ":stub", UUID.randomUUID().toString());
        }
        log.info("[SMS-{}] (would call {}) to={}", name, cfg.apiUrl(), m.to());
        return SmsResult.ok(name, UUID.randomUUID().toString());
    }

    private record Message(String to, String body) {}

    @SuppressWarnings("unused")
    private static final Map<String, String> EXAMPLES = Map.of(
            "chinguitel", "https://api.chinguitel.mr/sms/send",
            "mauritel",   "https://api.mauritel.mr/v1/sms");
}

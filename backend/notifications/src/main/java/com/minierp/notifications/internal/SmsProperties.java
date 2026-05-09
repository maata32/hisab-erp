package com.minierp.notifications.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.notifications.sms")
public record SmsProperties(
        String defaultProvider,
        List<String> fallbackOrder,
        Provider chinguitel,
        Provider mauritel) {

    public record Provider(
            String apiUrl,
            String apiKey,
            String sender) {}
}

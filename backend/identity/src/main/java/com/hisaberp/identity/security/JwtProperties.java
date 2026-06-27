package com.hisaberp.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret must be at least 32 characters long (256 bits) for HS256");
        }
        if (accessTokenTtl == null) accessTokenTtl = Duration.ofMinutes(15);
        if (refreshTokenTtl == null) refreshTokenTtl = Duration.ofDays(7);
        if (issuer == null) issuer = "hisab-erp";
        if (audience == null) audience = "hisab-erp-clients";
    }
}

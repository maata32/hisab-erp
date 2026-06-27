package com.hisaberp.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket,
        long maxFileSizeBytes) {

    /** Endpoint to expose in URLs handed to browsers; falls back to `endpoint`. */
    public String resolvedPublicEndpoint() {
        return publicEndpoint != null && !publicEndpoint.isBlank() ? publicEndpoint : endpoint;
    }
}

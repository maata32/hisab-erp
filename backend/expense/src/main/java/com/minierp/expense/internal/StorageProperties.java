package com.minierp.expense.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
record StorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        long maxFileSizeBytes) {}

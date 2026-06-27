package com.hisaberp.catalog.events;

import java.time.Instant;
import java.util.UUID;

public record ProductCreatedEvent(
        UUID tenantId,
        UUID productId,
        String sku,
        String name,
        Instant occurredAt) {}

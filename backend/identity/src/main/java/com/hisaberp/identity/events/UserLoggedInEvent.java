package com.hisaberp.identity.events;

import java.time.Instant;
import java.util.UUID;

public record UserLoggedInEvent(UUID userId, UUID tenantId, Instant occurredAt) {}

package com.hisaberp.pos.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashMovementDto(
        UUID id,
        UUID sessionId,
        String type,
        BigDecimal amount,
        String reason,
        Instant occurredAt) {}

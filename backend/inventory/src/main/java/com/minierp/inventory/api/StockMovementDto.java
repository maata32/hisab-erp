package com.minierp.inventory.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementDto(
        UUID id,
        UUID warehouseId,
        UUID productId,
        StockMovementType type,
        BigDecimal qtySigned,
        BigDecimal unitCost,
        String referenceType,
        UUID referenceId,
        String referenceNumber,
        String note,
        Instant occurredAt,
        UUID userId) {}

package com.minierp.pos.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashSessionDto(
        UUID id,
        UUID registerId,
        UUID cashierUserId,
        String status,
        Instant openedAt,
        Instant closedAt,
        BigDecimal openingFloat,
        BigDecimal expectedClosing,
        BigDecimal countedClosing,
        BigDecimal difference,
        BigDecimal totalSales,
        BigDecimal totalCashIn,
        BigDecimal totalCashOut,
        String note) {}

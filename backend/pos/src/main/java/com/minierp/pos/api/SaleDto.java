package com.minierp.pos.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SaleDto(
        UUID id,
        String number,
        String idempotencyKey,
        UUID registerId,
        UUID sessionId,
        UUID warehouseId,
        UUID cashierUserId,
        UUID customerId,
        String status,
        String currency,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal discountAmount,
        BigDecimal total,
        BigDecimal paidCash,
        BigDecimal paidCard,
        BigDecimal paidMobile,
        BigDecimal paidCredit,
        BigDecimal changeDue,
        Instant completedAt,
        String note,
        List<SaleLineDto> lines) {

    public record SaleLineDto(
            UUID id,
            int lineNumber,
            UUID productId,
            UUID uomId,
            BigDecimal quantity,
            BigDecimal baseQuantity,
            BigDecimal unitPrice,
            BigDecimal unitDiscount,
            BigDecimal taxRate,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total,
            boolean taxInclusive,
            String snapshotName,
            String snapshotSku) {}
}

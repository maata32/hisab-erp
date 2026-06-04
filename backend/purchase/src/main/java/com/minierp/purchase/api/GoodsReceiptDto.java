package com.minierp.purchase.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class GoodsReceiptDto {

    public record LineDto(
            UUID id,
            UUID productId,
            UUID uomId,
            BigDecimal quantityOrdered,
            BigDecimal quantityReceived,
            BigDecimal unitCost,
            UUID lotId,
            String status,
            String productName,
            String sku
    ) {}

    public record GoodsReceiptResponse(
            UUID id,
            String number,
            UUID supplierId,
            String supplierName,
            UUID purchaseInvoiceId,
            UUID warehouseId,
            String status,
            String type,
            LocalDate scheduledDate,
            Instant receivedAt,
            String notes,
            List<LineDto> lines,
            Instant createdAt
    ) {}

    /** One planned reception line. {@code quantityOrdered} = qty to receive in this BRC. */
    public record LineRequest(
            UUID productId,
            UUID uomId,
            BigDecimal quantityOrdered,
            BigDecimal unitCost,
            String productName,
            String sku
    ) {}

    public record CreateGoodsReceiptRequest(
            UUID supplierId,
            @NotNull UUID purchaseInvoiceId,
            UUID warehouseId,
            LocalDate scheduledDate,
            String notes,
            // When null/empty, the service seeds lines from the invoice's
            // outstanding (not-yet-received) quantities.
            @Valid List<LineRequest> lines
    ) {}

    public record RecordReceiptRequest(
            @NotNull @Valid List<LineReceived> lines,
            String notes
    ) {}

    /** Lot fields are required when the product has trackExpiry = true. */
    public record LineReceived(
            @NotNull UUID lineId,
            @NotNull BigDecimal quantityReceived,
            String lotNumber,
            LocalDate productionDate,
            LocalDate expirationDate
    ) {}
}

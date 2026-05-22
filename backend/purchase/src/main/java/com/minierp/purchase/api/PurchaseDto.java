package com.minierp.purchase.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class PurchaseDto {

    // ── Lines ────────────────────────────────────────────────────────────────

    public record LineRequest(
            @NotNull UUID productId,
            UUID uomId,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @PositiveOrZero BigDecimal unitCost,
            BigDecimal taxRate
    ) {}

    public record LineDto(
            UUID id,
            int lineNumber,
            UUID productId,
            UUID uomId,
            BigDecimal quantity,
            BigDecimal quantityReceived,
            BigDecimal unitCost,
            BigDecimal taxRate,
            BigDecimal lineTotal,
            String productName,
            String sku
    ) {}

    // ── Purchase orders ──────────────────────────────────────────────────────

    public record CreatePurchaseOrderRequest(
            @NotNull UUID supplierId,
            @NotNull UUID warehouseId,
            LocalDate orderDate,
            LocalDate expectedDate,
            String currency,
            String notes,
            @NotEmpty @Valid List<LineRequest> lines
    ) {}

    public record PurchaseOrderDto(
            UUID id,
            String number,
            UUID supplierId,
            String supplierName,
            UUID warehouseId,
            LocalDate orderDate,
            LocalDate expectedDate,
            String status,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total,
            String notes,
            List<LineDto> lines,
            Instant createdAt
    ) {}

    // ── Reception ────────────────────────────────────────────────────────────

    public record ReceiveLineRequest(
            @NotNull UUID purchaseOrderLineId,
            @NotNull @Positive BigDecimal quantityReceived,
            // Required when product.trackExpiry = true.
            String lotNumber,
            LocalDate productionDate,
            LocalDate expirationDate
    ) {}

    public record ReceivePurchaseOrderRequest(
            // Optional override for the PO header warehouse.
            UUID warehouseId,
            @NotEmpty @Valid List<ReceiveLineRequest> lines
    ) {}

    public record ReceiptLineResult(
            UUID purchaseOrderLineId,
            BigDecimal quantityReceived,
            UUID stockMovementId,
            UUID lotId
    ) {}

    public record ReceiptResult(
            UUID purchaseOrderId,
            String status,
            List<ReceiptLineResult> lines
    ) {}

    // ── Purchase invoices ────────────────────────────────────────────────────

    public record CreatePurchaseInvoiceRequest(
            @NotNull UUID supplierId,
            UUID purchaseOrderId,
            String supplierReference,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String currency,
            String notes,
            @NotEmpty @Valid List<LineRequest> lines
    ) {}

    public record PurchaseInvoiceDto(
            UUID id,
            String number,
            UUID supplierId,
            String supplierName,
            UUID purchaseOrderId,
            String supplierReference,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String status,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total,
            BigDecimal paidAmount,
            BigDecimal balance,
            String notes,
            List<LineDto> lines,
            Instant createdAt
    ) {}
}

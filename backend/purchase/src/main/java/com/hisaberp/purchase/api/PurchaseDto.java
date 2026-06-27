package com.hisaberp.purchase.api;

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
            @NotNull UUID variantId,
            UUID uomId,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @PositiveOrZero BigDecimal unitCost,
            BigDecimal taxRate
    ) {}

    public record LineDto(
            UUID id,
            int lineNumber,
            UUID variantId,
            UUID productId,
            UUID uomId,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal taxRate,
            BigDecimal lineTotal,
            String productName,
            String sku
    ) {}

    // ── Purchase orders ──────────────────────────────────────────────────────

    public record CreatePurchaseOrderRequest(
            @NotNull UUID supplierId,
            // Optional hint; the actual warehouse is chosen on the goods-receipt.
            UUID warehouseId,
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
            UUID convertedToInvoiceId,
            List<LineDto> lines,
            Instant createdAt
    ) {}

    /** Body of {@code POST /purchase-orders/{id}/convert}. */
    public record ConvertOrderToInvoiceRequest(
            LocalDate dueDate,
            String supplierReference
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
            String receptionStatus,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total,
            BigDecimal paidAmount,
            BigDecimal balance,
            String notes,
            List<LineDto> lines,
            long creditNoteCount,
            Instant createdAt
    ) {}

    // ── Purchase credit notes (avoirs) ────────────────────────────────────────

    public record CreatePurchaseCreditNoteRequest(
            String reason
    ) {}

    public record PurchaseCreditNoteLineDto(
            UUID id,
            int lineNumber,
            UUID variantId,
            UUID productId,
            UUID uomId,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal taxRate,
            BigDecimal lineTotal,
            BigDecimal returnedToStockQty,
            String productName,
            String sku
    ) {}

    public record PurchaseCreditNoteDto(
            UUID id,
            String number,
            UUID purchaseInvoiceId,
            UUID supplierId,
            String supplierName,
            LocalDate issueDate,
            String reason,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total,
            BigDecimal amount,
            String status,
            String currency,
            List<PurchaseCreditNoteLineDto> lines,
            Instant createdAt
    ) {}

    public record PurchaseCreditNoteReturnLineDto(
            UUID variantId,
            UUID productId,
            String productName,
            String sku,
            BigDecimal returnQty
    ) {}

    public record PurchaseCreditNotePreviewDto(
            UUID purchaseInvoiceId,
            String invoiceNumber,
            BigDecimal total,
            // null when the avoir can be issued; otherwise a reason code.
            String blockReason,
            BigDecimal alreadyPaid,
            List<PurchaseCreditNoteReturnLineDto> returnLines
    ) {}
}

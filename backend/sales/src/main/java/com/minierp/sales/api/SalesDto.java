package com.minierp.sales.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class SalesDto {

    public record LineRequest(
            UUID productId,
            UUID uomId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPercent
    ) {}

    public record LineDto(
            UUID id,
            int lineNumber,
            UUID productId,
            UUID uomId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPercent,
            BigDecimal taxRate,
            BigDecimal lineTotal,
            String productName,
            String sku
    ) {}

    public record QuoteDto(
            UUID id,
            String number,
            UUID customerId,
            String customerName,
            LocalDate issueDate,
            LocalDate validUntil,
            String status,
            String currency,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal total,
            String notes,
            List<LineDto> lines,
            Instant createdAt
    ) {}

    public record OrderDto(
            UUID id,
            String number,
            UUID customerId,
            String customerName,
            UUID quoteId,
            LocalDate orderDate,
            String status,
            boolean deliveryRequired,
            String currency,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal total,
            String notes,
            List<LineDto> lines,
            Instant createdAt
    ) {}

    public record InvoiceDto(
            UUID id,
            String number,
            UUID customerId,
            String customerName,
            UUID orderId,
            LocalDate issueDate,
            LocalDate dueDate,
            String status,
            String currency,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal total,
            BigDecimal paidAmount,
            BigDecimal balance,
            String paymentTerms,
            String notes,
            List<LineDto> lines,
            Instant createdAt
    ) {}

    public record CreditNoteLineDto(
            UUID id,
            UUID invoiceLineId,
            UUID productId,
            UUID uomId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPercent,
            BigDecimal taxRate,
            BigDecimal lineTotal,
            BigDecimal returnedToStockQty,
            String productName,
            String sku
    ) {}

    public record CreditNoteDto(
            UUID id,
            String number,
            UUID invoiceId,
            String invoiceNumber,
            UUID customerId,
            String customerName,
            LocalDate issueDate,
            String reason,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total,
            String status,
            String currency,
            List<CreditNoteLineDto> lines,
            Instant createdAt
    ) {}

    public record CreateQuoteRequest(
            UUID customerId,
            LocalDate issueDate,
            LocalDate validUntil,
            String currency,
            String notes,
            List<LineRequest> lines
    ) {}

    public record CreateOrderRequest(
            UUID customerId,
            UUID quoteId,
            LocalDate orderDate,
            boolean deliveryRequired,
            String currency,
            String notes,
            List<LineRequest> lines
    ) {}

    public record CreateInvoiceRequest(
            UUID customerId,
            UUID orderId,
            LocalDate issueDate,
            LocalDate dueDate,
            String paymentTerms,
            String currency,
            String notes,
            List<LineRequest> lines
    ) {}

    public record CreateCreditNoteLine(
            UUID invoiceLineId,
            BigDecimal quantity
    ) {}

    public record CreateCreditNoteRequest(
            String reason,
            List<CreateCreditNoteLine> lines
    ) {}

    public record CreditableLineDto(
            UUID invoiceLineId,
            UUID productId,
            String productName,
            String sku,
            UUID uomId,
            BigDecimal quantityInvoiced,
            BigDecimal alreadyCredited,
            BigDecimal maxCreditable,
            BigDecimal unitPrice,
            BigDecimal discountPercent,
            BigDecimal taxRate
    ) {}

    public record CreditableInvoiceDto(
            UUID invoiceId,
            String invoiceNumber,
            UUID customerId,
            String customerName,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total,
            List<CreditableLineDto> lines
    ) {}
}

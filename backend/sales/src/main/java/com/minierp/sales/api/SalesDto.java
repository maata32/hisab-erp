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

    public record CreditNoteDto(
            UUID id,
            String number,
            UUID invoiceId,
            UUID customerId,
            LocalDate issueDate,
            String reason,
            BigDecimal amount,
            String status,
            String currency,
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

    public record CreateCreditNoteRequest(
            UUID invoiceId,
            String reason,
            BigDecimal amount
    ) {}
}

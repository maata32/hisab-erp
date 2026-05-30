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
            Instant createdAt,
            UUID linkedInvoiceId,
            String linkedInvoiceNumber,
            String linkedInvoiceStatus
    ) {}

    public record InvoiceDto(
            UUID id,
            String number,
            UUID customerId,
            String customerName,
            UUID quoteId,
            LocalDate issueDate,
            LocalDate dueDate,
            String status,
            String deliveryStatus,
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
            Instant createdAt,
            String quoteNumber,
            String quoteStatus,
            long creditNoteCount
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

    public record UpdateQuoteRequest(
            LocalDate issueDate,
            LocalDate validUntil,
            String currency,
            String notes,
            List<LineRequest> lines
    ) {}

    public record ConvertQuoteToInvoiceRequest(
            LocalDate dueDate,
            String paymentTerms
    ) {}

    public record CreateInvoiceRequest(
            UUID customerId,
            UUID quoteId,
            LocalDate issueDate,
            LocalDate dueDate,
            String paymentTerms,
            String currency,
            String notes,
            List<LineRequest> lines
    ) {}

    public record CreateCreditNoteRequest(
            String reason
    ) {}

    /**
     * One product row that will appear on the auto-generated BR if a credit
     * note is issued now. Only products with delivered_qty > 0 show up here:
     * never-shipped lines cancel without a stock movement.
     */
    public record CreditNoteReturnLineDto(
            UUID productId,
            String productName,
            String sku,
            UUID uomId,
            BigDecimal quantityToReturn
    ) {}

    /**
     * Impact preview shown before validating an avoir total. The UI uses this
     * to warn the user about (1) how much of the invoice will end up as
     * customer credit (OVERPAYMENT) because it was already paid, and (2) which
     * BR will be auto-created for delivered items.
     */
    public record CreditNotePreviewDto(
            UUID invoiceId,
            String invoiceNumber,
            UUID customerId,
            String customerName,
            String currency,
            BigDecimal invoiceTotal,
            BigDecimal alreadyPaidAmount,
            BigDecimal invoiceBalance,
            BigDecimal creditNoteAmount,
            BigDecimal amountToCustomerCredit,
            boolean willCreateReturnBl,
            List<CreditNoteReturnLineDto> returnLines,
            String blockReason
    ) {}
}

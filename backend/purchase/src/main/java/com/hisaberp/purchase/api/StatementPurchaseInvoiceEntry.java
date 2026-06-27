package com.hisaberp.purchase.api;

import com.hisaberp.sales.api.StatementInvoiceLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Purchase invoice projection for the unified partner statement. Mirrors
 * {@link com.hisaberp.sales.api.StatementInvoiceEntry}. The {@code lines} field
 * is populated only for a detailed statement; otherwise it is null. Lines reuse
 * the sales {@link StatementInvoiceLine} record so the statement template renders
 * both sides identically (unitPrice carries the purchase unit cost).
 */
public record StatementPurchaseInvoiceEntry(
        UUID id,
        String number,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal balance,
        String status,
        List<StatementInvoiceLine> lines,
        Instant createdAt
) {}

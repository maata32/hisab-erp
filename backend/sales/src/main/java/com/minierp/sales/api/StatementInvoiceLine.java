package com.minierp.sales.api;

import java.math.BigDecimal;

/** Single line of an invoice, used by detailed customer statements. */
public record StatementInvoiceLine(
        String productName,
        String sku,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountPercent,
        BigDecimal lineTotal
) {}

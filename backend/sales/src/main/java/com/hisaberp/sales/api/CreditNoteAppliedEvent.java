package com.hisaberp.sales.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published synchronously at the end of {@code SalesService.createCreditNote},
 * after the credit note has reduced the invoice balance through
 * {@code applyCredit}. The {@code imputedAmount} reflects the amount actually
 * applied to the invoice (capped at {@code invoice.balance}); any surplus has
 * already been grouped into a fresh {@code CUSTOMER_CREDIT} row by the surplus
 * branch of the same flow and is not represented here.
 *
 * <p>Used by the AllocationEngine to mirror the pairing in the unified
 * {@code allocations} audit table (positive = CREDIT_NOTE, negative = INVOICE)
 * so every credit application — by payment, customer credit, or avoir — is
 * visible from the same query.</p>
 */
public record CreditNoteAppliedEvent(
        UUID creditNoteId,
        String creditNoteNumber,
        UUID invoiceId,
        UUID partyId,
        BigDecimal imputedAmount) {}

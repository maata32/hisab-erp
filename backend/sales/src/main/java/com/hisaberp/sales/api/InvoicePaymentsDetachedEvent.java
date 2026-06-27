package com.hisaberp.sales.api;

import java.util.UUID;

/**
 * Published synchronously inside {@code SalesService.createCreditNote} when a
 * total avoir is issued against an invoice that still carries payments. Issuing
 * the avoir detaches the invoice from its payments: the invoice is settled
 * (lettered) by the credit note instead, and the cash already received is freed
 * back to the customer as a refundable credit.
 *
 * <p>The AllocationEngine listens to this to (1) soft-void the
 * {@code PAYMENT → INVOICE} rows of the invoice in the unified
 * {@code allocations} table, and (2) mint, for each detached payment, an
 * {@code OVERPAYMENT} customer credit stamped with the originating payment id so
 * the engine never double-counts the freed payment.</p>
 */
public record InvoicePaymentsDetachedEvent(
        UUID invoiceId,
        UUID partyId,
        String creditNoteNumber) {}

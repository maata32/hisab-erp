package com.hisaberp.purchase.api;

import java.util.UUID;

/**
 * Published synchronously inside {@code PurchaseService.createPurchaseCreditNote}
 * when a total avoir is issued against a purchase invoice that still carries
 * supplier payments. Issuing the avoir detaches the invoice from its payments:
 * the avoir settles (letters) the invoice instead, and the cash already paid to
 * the supplier is freed back.
 *
 * <p>Unlike the sales side (which mints an OVERPAYMENT customer credit), the
 * AllocationEngine listener here only soft-voids the
 * {@code SUPPLIER_PAYMENT → PURCHASE_INVOICE} rows — the supplier-payment
 * residual then reopens automatically as an available open item.</p>
 */
public record PurchaseInvoicePaymentsDetachedEvent(
        UUID purchaseInvoiceId,
        UUID partyId,
        String creditNoteNumber) {}

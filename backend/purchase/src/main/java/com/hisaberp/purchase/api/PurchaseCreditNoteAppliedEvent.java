package com.hisaberp.purchase.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published synchronously at the end of {@code PurchaseService.createPurchaseCreditNote},
 * after the avoir has reduced the purchase-invoice balance through applyCredit.
 * Mirror of the sales {@code CreditNoteAppliedEvent}: the AllocationEngine writes
 * the {@code PURCHASE_CREDIT_NOTE → PURCHASE_INVOICE} pairing into the unified
 * {@code allocations} audit table.
 */
public record PurchaseCreditNoteAppliedEvent(
        UUID creditNoteId,
        String creditNoteNumber,
        UUID purchaseInvoiceId,
        UUID partyId,
        BigDecimal imputedAmount) {}

package com.minierp.sales.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Published synchronously when a credit note covers goods that have already
 * shipped. Listened to by the delivery module, which creates a RETURN-typed
 * BL and posts the corresponding stock-in. The handler runs in the same
 * transaction as the credit-note creation, so credit-note + return BL +
 * stock receipt are atomic.
 */
public record CreditNoteReturnRequestedEvent(
        UUID creditNoteId,
        String creditNoteNumber,
        UUID invoiceId,
        UUID partyId,
        List<ReturnLine> lines
) {
    public record ReturnLine(
            UUID productId,
            UUID uomId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String productName,
            String sku
    ) {}
}

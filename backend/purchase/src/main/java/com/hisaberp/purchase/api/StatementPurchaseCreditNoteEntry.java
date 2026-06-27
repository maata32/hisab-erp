package com.hisaberp.purchase.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Supplier credit note (avoir fournisseur) projection for the unified partner
 * statement. Mirrors {@link com.hisaberp.sales.api.StatementCreditNoteEntry}.
 */
public record StatementPurchaseCreditNoteEntry(
        UUID id,
        UUID purchaseInvoiceId,
        String number,
        LocalDate issueDate,
        BigDecimal amount,
        String reason,
        String status,
        Instant createdAt
) {}

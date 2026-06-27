package com.hisaberp.allocation.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the unified {@code allocations} audit table for a party, enriched
 * with human-readable labels for both sides. Unlike {@link OpenItem} (which is a
 * snapshot of what is still open <em>now</em>), this exposes the full pairing
 * history over time — including rows that were later reversed (soft-void).
 *
 * @param id             allocation row id
 * @param positiveType   source-type of the positive side (e.g. {@code PAYMENT},
 *                       {@code CUSTOMER_CREDIT}, {@code CREDIT_NOTE})
 * @param positiveId     id of the positive-side item
 * @param positiveLabel  human-readable label for the positive side (document
 *                       number, credit source…), or a short id fallback
 * @param negativeType   source-type of the negative side (e.g. {@code INVOICE},
 *                       {@code PURCHASE_INVOICE}, {@code PAYMENT})
 * @param negativeId     id of the negative-side item
 * @param negativeLabel  human-readable label for the negative side
 * @param amount         allocated amount (always > 0; see the table CHECK)
 * @param allocatedAt    when the pairing was made
 * @param notes          free-text note captured at allocation time
 * @param reversedAt     non-null when the row was soft-voided (e.g. the source
 *                       payment was refunded); {@code null} for active rows
 * @param reversalReason why the row was reversed, when applicable
 */
public record AllocationHistoryRow(
        UUID id,
        String positiveType,
        UUID positiveId,
        String positiveLabel,
        String negativeType,
        UUID negativeId,
        String negativeLabel,
        BigDecimal amount,
        Instant allocatedAt,
        String notes,
        Instant reversedAt,
        String reversalReason) {}

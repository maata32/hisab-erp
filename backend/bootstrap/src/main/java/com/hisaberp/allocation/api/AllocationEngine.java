package com.hisaberp.allocation.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read-only Phase 1 contract for the unified allocation engine. Phase 2 will
 * add write operations ({@code apply}, {@code unapply}) and wire the existing
 * sales / payment / credit-note services through them.
 *
 * <p>The engine works per-party: open items are positive (cash brought) and
 * negative (cash consumed) from the operator's perspective. Allocation pairs
 * a positive with a negative for the same party in FIFO order (by date_ref).</p>
 */
public interface AllocationEngine {

    /**
     * All financial items still carrying an open balance for the given party,
     * across modules (invoices, payments, credits…). Items are computed on the
     * fly from existing tables and the new {@code allocations} table.
     */
    List<OpenItem> findOpenItemsByParty(UUID partyId);

    /**
     * Full pairing history from the {@code allocations} audit table for the
     * given party, newest first, with human-readable labels resolved for both
     * sides. Includes reversed (soft-void) rows so the screen can show what was
     * allocated and later undone. Read-only.
     */
    List<AllocationHistoryRow> findAllocationHistoryByParty(UUID partyId);

    /**
     * Propose a FIFO allocation for a source item against open items of the
     * opposite sign. The amount left over (if any) appears in
     * {@link AllocationProposal#surplus()}.
     *
     * @param partyId    the party of the source item
     * @param sourceType matches {@link OpenItem#sourceType()}
     * @param sourceId   matches {@link OpenItem#sourceId()}
     * @param amount     the amount to allocate (typically the source item's
     *                   open balance, but the caller may pass less for a
     *                   partial allocation)
     */
    AllocationProposal propose(UUID partyId, String sourceType, UUID sourceId, BigDecimal amount);

    /**
     * Generic netting of any POSITIVE open item against any NEGATIVE one for the
     * same party (unified net ledger): customer invoice ↔ supplier invoice
     * (compensation), payment ↔ invoice on either side, credit ↔ invoice, two
     * payments, etc. Caps to {@code min(amount, positive.open, negative.open)},
     * reduces each side's open balance through the owning module, and records one
     * row in {@code allocations}. Amounts stay positive — the sign is the side.
     *
     * @return the amount actually netted.
     */
    BigDecimal apply(UUID partyId,
                     String positiveType, UUID positiveId,
                     String negativeType, UUID negativeId,
                     BigDecimal amount);

    /**
     * Apply a customer credit (positive) against an invoice (negative): reduces
     * the invoice balance via the sales-side {@code applyCredit} flow, marks
     * the credit row as partly or fully consumed, and persists one row in the
     * {@code allocations} audit table.
     *
     * @return the amount actually applied (capped by min(invoice.balance,
     *         credit.remaining, amount)).
     */
    BigDecimal applyCreditToInvoice(UUID creditId, UUID invoiceId, BigDecimal amount);

    /**
     * Apply a customer credit (positive) against a CASH_OUT_REFUND payment
     * (negative). The refund payment must already exist (typically just-created
     * by the payment dialog) and belong to the same party as the credit. The
     * call decrements the credit's {@code remaining_amount} and records the
     * pairing in {@code allocations}; no cash movement is generated — the
     * caller is responsible for the payment itself.
     *
     * @return the amount actually consumed (capped by credit.remaining and
     *         payment.amount).
     */
    BigDecimal applyCreditToRefund(UUID creditId, UUID refundPaymentId, BigDecimal amount);

    /**
     * Impute an open supplier "versement" (a {@code CASH_IN_REFUND} payment —
     * cash the supplier gave back) against a supplier "retrait" (a
     * {@code CASH_OUT} payment — cash we paid out). Both payments must
     * already exist, be CONFIRMED, and belong to the same supplier. The call
     * records the pairing in {@code allocations} (positive side = the versement,
     * negative side = the retrait) and thereby consumes the versement's open
     * residual; no cash movement is generated and neither payment's amount nor
     * any invoice balance is changed — this is a traceability link in the
     * statement, mirroring {@link #applyCreditToRefund}.
     *
     * @return the amount actually consumed (capped by the versement's remaining
     *         open residual and the retrait's amount).
     */
    BigDecimal applySupplierRefundToRetrait(UUID refundPaymentId, UUID retraitPaymentId, BigDecimal amount);
}

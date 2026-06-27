package com.hisaberp.allocation.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A financial item attached to a party, still open for allocation against
 * an opposite-signed item. Computed on the fly from existing tables — see
 * {@link AllocationEngine#findOpenItemsByParty(UUID)}.
 *
 * <p>Sign convention is from the operator's perspective:</p>
 * <ul>
 *   <li>{@code NEGATIVE} = consumes funds (e.g. customer invoice the operator
 *       expects to be paid for, refund-out the operator must pay).</li>
 *   <li>{@code POSITIVE} = brings funds (e.g. payment received not yet
 *       allocated, customer credit in the customer's favour, deposit).</li>
 * </ul>
 *
 * @param sourceType  one of {@code INVOICE, PAYMENT, CUSTOMER_CREDIT,
 *                    PURCHASE_INVOICE, SUPPLIER_PAYMENT}. Stable string —
 *                    persisted into {@code allocations.positive_type} /
 *                    {@code allocations.negative_type}.
 * @param sign        whether this item is to be allocated as positive or
 *                    negative.
 * @param amountTotal the item's face value.
 * @param amountOpen  the value still unallocated.
 * @param dateRef     issue/payment date — used as FIFO sort key.
 * @param dueDate     due date if applicable (invoices), {@code null} otherwise.
 * @param status      original status string from the source table.
 * @param label       human-readable label (e.g. invoice number).
 */
public record OpenItem(
        String sourceType,
        UUID sourceId,
        Sign sign,
        BigDecimal amountTotal,
        BigDecimal amountOpen,
        LocalDate dateRef,
        LocalDate dueDate,
        String status,
        String label) {

    public enum Sign { POSITIVE, NEGATIVE }
}

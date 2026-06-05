package com.minierp.purchase.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only projection of purchase documents (invoices + supplier credit notes)
 * for the unified partner statement. Mirror of
 * {@link com.minierp.sales.api.SalesStatementLookup}; lets the aggregating
 * controller in the bootstrap module build the supplier side of the statement
 * without touching purchase internals.
 */
public interface PurchaseStatementLookup {

    /**
     * Purchase invoices for a supplier with invoiceDate in [{@code from}, {@code to}],
     * excluding DRAFT and CANCELLED. Pass {@code detailed=true} to also load product
     * lines; otherwise {@code lines} is null.
     */
    List<StatementPurchaseInvoiceEntry> findInvoicesForStatement(
            UUID supplierId, LocalDate from, LocalDate to, boolean detailed);

    /** Non-draft supplier credit notes for a supplier with issueDate in [{@code from}, {@code to}]. */
    List<StatementPurchaseCreditNoteEntry> findCreditNotesForStatement(
            UUID supplierId, LocalDate from, LocalDate to);
}

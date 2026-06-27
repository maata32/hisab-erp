package com.hisaberp.sales.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only projection of sales documents (invoices + credit notes) for the
 * customer statement of account. Exposed so the aggregating controller in
 * the bootstrap module can build the statement without touching internals.
 */
public interface SalesStatementLookup {
    /**
     * Invoices issued for a customer with issueDate in [{@code from}, {@code to}],
     * excluding DRAFT and CANCELLED. Pass {@code detailed=true} to also load product lines
     * (each entry's {@code lines} field is populated); otherwise {@code lines} is null.
     */
    List<StatementInvoiceEntry> findInvoicesForStatement(
            UUID customerId, LocalDate from, LocalDate to, boolean detailed);

    /** Credit notes issued for a customer with issueDate in [{@code from}, {@code to}]. */
    List<StatementCreditNoteEntry> findCreditNotesForStatement(
            UUID customerId, LocalDate from, LocalDate to);
}

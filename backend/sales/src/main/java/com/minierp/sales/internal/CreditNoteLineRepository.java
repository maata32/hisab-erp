package com.minierp.sales.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface CreditNoteLineRepository extends JpaRepository<CreditNoteLine, UUID> {

    List<CreditNoteLine> findByCreditNoteIdOrderByLineNumberAsc(UUID creditNoteId);

    /**
     * Total quantity already credited per invoice line, across all credit notes
     * issued against this invoice. Returned rows are (invoice_line_id, sum_quantity).
     */
    @Query("""
            SELECT cnl.invoiceLineId, SUM(cnl.quantity)
            FROM CreditNoteLine cnl, CreditNote cn
            WHERE cnl.creditNoteId = cn.id
              AND cn.invoiceId = :invoiceId
              AND cn.status <> com.minierp.sales.internal.CreditNoteStatus.DRAFT
              AND cnl.invoiceLineId IS NOT NULL
            GROUP BY cnl.invoiceLineId
            """)
    List<Object[]> sumQuantityByInvoiceLine(@Param("invoiceId") UUID invoiceId);

    /**
     * Total quantity already returned to stock per product, across all credit
     * notes issued against this invoice.
     */
    @Query("""
            SELECT cnl.productId, SUM(cnl.returnedToStockQty)
            FROM CreditNoteLine cnl, CreditNote cn
            WHERE cnl.creditNoteId = cn.id
              AND cn.invoiceId = :invoiceId
              AND cn.status <> com.minierp.sales.internal.CreditNoteStatus.DRAFT
            GROUP BY cnl.productId
            """)
    List<Object[]> sumReturnedByProduct(@Param("invoiceId") UUID invoiceId);

    /**
     * Total credited quantity per product across all non-draft credit notes
     * issued against this invoice. Used by recomputeDeliveryStatus to subtract
     * credited amount from the invoiced amount before comparing to delivered.
     */
    @Query("""
            SELECT cnl.productId, SUM(cnl.quantity)
            FROM CreditNoteLine cnl, CreditNote cn
            WHERE cnl.creditNoteId = cn.id
              AND cn.invoiceId = :invoiceId
              AND cn.status <> com.minierp.sales.internal.CreditNoteStatus.DRAFT
            GROUP BY cnl.productId
            """)
    List<Object[]> sumCreditedByProduct(@Param("invoiceId") UUID invoiceId);
}

package com.hisaberp.sales.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface CreditNoteLineRepository extends JpaRepository<CreditNoteLine, UUID> {

    List<CreditNoteLine> findByCreditNoteIdOrderByLineNumberAsc(UUID creditNoteId);

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
              AND cn.status <> com.hisaberp.sales.internal.CreditNoteStatus.DRAFT
            GROUP BY cnl.productId
            """)
    List<Object[]> sumCreditedByProduct(@Param("invoiceId") UUID invoiceId);
}

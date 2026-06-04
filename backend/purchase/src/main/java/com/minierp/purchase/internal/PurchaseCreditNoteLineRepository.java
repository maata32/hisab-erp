package com.minierp.purchase.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface PurchaseCreditNoteLineRepository extends JpaRepository<PurchaseCreditNoteLine, UUID> {

    List<PurchaseCreditNoteLine> findByPurchaseCreditNoteIdOrderByLineNumberAsc(UUID purchaseCreditNoteId);

    /**
     * Total credited quantity per product across all non-draft purchase credit
     * notes issued against this invoice. Used by recomputeReceptionStatus to
     * subtract credited amount from invoiced amount before comparing to received.
     * Mirror of {@code CreditNoteLineRepository.sumCreditedByProduct}.
     */
    @Query("""
            SELECT cnl.productId, SUM(cnl.quantity)
            FROM PurchaseCreditNoteLine cnl, PurchaseCreditNote cn
            WHERE cnl.purchaseCreditNoteId = cn.id
              AND cn.purchaseInvoiceId = :invoiceId
              AND cn.status <> com.minierp.purchase.internal.PurchaseCreditNoteStatus.DRAFT
            GROUP BY cnl.productId
            """)
    List<Object[]> sumCreditedByProduct(@Param("invoiceId") UUID invoiceId);
}

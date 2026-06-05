package com.minierp.purchase.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface PurchaseCreditNoteRepository extends JpaRepository<PurchaseCreditNote, UUID> {
    Page<PurchaseCreditNote> findByPartyId(UUID partyId, Pageable pageable);
    Page<PurchaseCreditNote> findByPurchaseInvoiceId(UUID purchaseInvoiceId, Pageable pageable);
    Page<PurchaseCreditNote> findAll(Pageable pageable);

    @Query("""
            SELECT cn.purchaseInvoiceId, COUNT(cn)
            FROM PurchaseCreditNote cn
            WHERE cn.purchaseInvoiceId IN :ids
              AND cn.status <> com.minierp.purchase.internal.PurchaseCreditNoteStatus.DRAFT
            GROUP BY cn.purchaseInvoiceId
            """)
    List<Object[]> countNonDraftByInvoiceIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT COUNT(cn)
            FROM PurchaseCreditNote cn
            WHERE cn.purchaseInvoiceId = :invoiceId
              AND cn.status <> com.minierp.purchase.internal.PurchaseCreditNoteStatus.DRAFT
            """)
    long countNonDraftByInvoiceId(@Param("invoiceId") UUID invoiceId);

    @Query("SELECT cn FROM PurchaseCreditNote cn WHERE cn.partyId = :partyId " +
           "AND cn.status <> com.minierp.purchase.internal.PurchaseCreditNoteStatus.DRAFT " +
           "AND cn.issueDate >= :from AND cn.issueDate <= :to " +
           "ORDER BY cn.issueDate ASC, cn.createdAt ASC")
    List<PurchaseCreditNote> findForStatement(@Param("partyId") UUID partyId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}

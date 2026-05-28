package com.minierp.sales.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {
    Page<CreditNote> findByPartyId(UUID partyId, Pageable pageable);
    Page<CreditNote> findByInvoiceId(UUID invoiceId, Pageable pageable);
    Page<CreditNote> findAll(Pageable pageable);

    List<CreditNote> findByInvoiceIdAndStatusNot(UUID invoiceId, CreditNoteStatus status);

    /**
     * Non-draft credit-note count grouped by invoice, for a set of invoice ids
     * — used by the invoice list to surface a badge without an N+1 query.
     */
    @Query("""
            SELECT cn.invoiceId, COUNT(cn)
            FROM CreditNote cn
            WHERE cn.invoiceId IN :ids
              AND cn.status <> com.minierp.sales.internal.CreditNoteStatus.DRAFT
            GROUP BY cn.invoiceId
            """)
    List<Object[]> countNonDraftByInvoiceIds(@Param("ids") java.util.Collection<UUID> ids);

    @Query("SELECT cn FROM CreditNote cn WHERE cn.partyId = :partyId " +
           "AND cn.issueDate >= :from AND cn.issueDate <= :to " +
           "ORDER BY cn.issueDate ASC")
    List<CreditNote> findForStatement(UUID partyId, LocalDate from, LocalDate to);
}

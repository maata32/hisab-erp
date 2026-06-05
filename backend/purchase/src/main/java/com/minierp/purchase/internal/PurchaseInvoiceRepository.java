package com.minierp.purchase.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, UUID> {

    Page<PurchaseInvoice> findByPartyId(UUID partyId, Pageable pageable);

    @Query("SELECT pi FROM PurchaseInvoice pi WHERE " +
           "(:partyId IS NULL OR pi.partyId = :partyId) AND " +
           "(:status IS NULL OR pi.status = :status)")
    Page<PurchaseInvoice> findFiltered(UUID partyId, PurchaseInvoiceStatus status, Pageable pageable);

    @Query("SELECT pi FROM PurchaseInvoice pi WHERE pi.partyId = :partyId " +
           "AND pi.status IN ('ISSUED','PARTIAL') ORDER BY pi.dueDate ASC NULLS LAST, pi.invoiceDate ASC")
    List<PurchaseInvoice> findUnpaidByParty(UUID partyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pi FROM PurchaseInvoice pi WHERE pi.id = :id")
    Optional<PurchaseInvoice> lockById(UUID id);

    @Query("SELECT pi FROM PurchaseInvoice pi WHERE pi.partyId = :partyId " +
           "AND pi.status NOT IN ('DRAFT','CANCELLED') " +
           "AND pi.invoiceDate >= :from AND pi.invoiceDate <= :to " +
           "ORDER BY pi.invoiceDate ASC, pi.createdAt ASC")
    List<PurchaseInvoice> findForStatement(UUID partyId, LocalDate from, LocalDate to);
}

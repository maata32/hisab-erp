package com.minierp.sales.internal;

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

interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Page<Invoice> findByPartyId(UUID partyId, Pageable pageable);
    Page<Invoice> findAll(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> lockById(UUID id);

    @Query("SELECT i FROM Invoice i WHERE i.status NOT IN ('PAID','CANCELLED','REFUNDED') AND i.dueDate < :today")
    List<Invoice> findOverdue(LocalDate today);

    @Query("SELECT i FROM Invoice i WHERE i.partyId = :partyId AND i.status NOT IN ('PAID','CANCELLED','REFUNDED') ORDER BY i.dueDate ASC")
    List<Invoice> findUnpaidByPartyOrderByDueDate(UUID partyId);

    @Query("SELECT i FROM Invoice i WHERE i.quoteId = :quoteId AND i.status <> 'CANCELLED' ORDER BY i.createdAt DESC")
    List<Invoice> findActiveByQuoteId(UUID quoteId);

    Optional<Invoice> findFirstByQuoteIdOrderByCreatedAtDesc(UUID quoteId);

    @Query("SELECT i FROM Invoice i WHERE i.partyId = :partyId " +
           "AND i.status <> 'CANCELLED' " +
           "AND i.issueDate >= :from AND i.issueDate <= :to " +
           "ORDER BY i.issueDate ASC, i.createdAt ASC")
    List<Invoice> findForStatement(UUID partyId, LocalDate from, LocalDate to);
}

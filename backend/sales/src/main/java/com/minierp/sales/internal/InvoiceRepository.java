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
    Page<Invoice> findByCustomerId(UUID customerId, Pageable pageable);
    Page<Invoice> findAll(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> lockById(UUID id);

    @Query("SELECT i FROM Invoice i WHERE i.status NOT IN ('PAID','CANCELLED') AND i.dueDate < :today")
    List<Invoice> findOverdue(LocalDate today);

    @Query("SELECT i FROM Invoice i WHERE i.customerId = :customerId AND i.status NOT IN ('PAID','CANCELLED') ORDER BY i.dueDate ASC")
    List<Invoice> findUnpaidByCustomerOrderByDueDate(UUID customerId);
}

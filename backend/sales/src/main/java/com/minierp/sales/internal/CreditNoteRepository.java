package com.minierp.sales.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {
    Page<CreditNote> findByCustomerId(UUID customerId, Pageable pageable);
    Page<CreditNote> findAll(Pageable pageable);

    @Query("SELECT cn FROM CreditNote cn WHERE cn.customerId = :customerId " +
           "AND cn.issueDate >= :from AND cn.issueDate <= :to " +
           "ORDER BY cn.issueDate ASC")
    List<CreditNote> findForStatement(UUID customerId, LocalDate from, LocalDate to);
}

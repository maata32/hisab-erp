package com.minierp.sales.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {
    Page<CreditNote> findByCustomerId(UUID customerId, Pageable pageable);
    Page<CreditNote> findAll(Pageable pageable);
}

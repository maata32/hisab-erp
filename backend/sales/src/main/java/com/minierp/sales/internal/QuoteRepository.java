package com.minierp.sales.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface QuoteRepository extends JpaRepository<Quote, UUID> {
    Page<Quote> findByCustomerId(UUID customerId, Pageable pageable);
    Page<Quote> findAll(Pageable pageable);
}

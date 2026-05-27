package com.minierp.sales.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByPartyId(UUID partyId, Pageable pageable);
    Page<Order> findAll(Pageable pageable);

    /** Latest order created from a given quote — used to surface the linked BC status on the quote list. */
    Optional<Order> findFirstByQuoteIdOrderByCreatedAtDesc(UUID quoteId);
}

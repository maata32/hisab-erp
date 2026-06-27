package com.hisaberp.sales.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface QuoteRepository extends JpaRepository<Quote, UUID> {
    Page<Quote> findByPartyId(UUID partyId, Pageable pageable);
    Page<Quote> findAll(Pageable pageable);
}

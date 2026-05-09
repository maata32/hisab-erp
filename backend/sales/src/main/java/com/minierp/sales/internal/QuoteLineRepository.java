package com.minierp.sales.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface QuoteLineRepository extends JpaRepository<QuoteLine, UUID> {
    List<QuoteLine> findByQuoteIdOrderByLineNumberAsc(UUID quoteId);
}

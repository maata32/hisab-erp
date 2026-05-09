package com.minierp.sales.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {
    List<InvoiceLine> findByInvoiceIdOrderByLineNumberAsc(UUID invoiceId);
}

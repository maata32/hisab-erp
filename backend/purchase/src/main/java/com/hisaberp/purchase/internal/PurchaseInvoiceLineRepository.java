package com.hisaberp.purchase.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PurchaseInvoiceLineRepository extends JpaRepository<PurchaseInvoiceLine, UUID> {
    List<PurchaseInvoiceLine> findByPurchaseInvoiceIdOrderByLineNumberAsc(UUID purchaseInvoiceId);
}

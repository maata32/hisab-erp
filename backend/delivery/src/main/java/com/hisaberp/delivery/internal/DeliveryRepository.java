package com.hisaberp.delivery.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
    Page<Delivery> findByPartyId(UUID partyId, Pageable pageable);
    Page<Delivery> findByInvoiceId(UUID invoiceId, Pageable pageable);
    Page<Delivery> findAll(Pageable pageable);
}

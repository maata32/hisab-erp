package com.minierp.payment.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Page<Payment> findByPartyId(UUID partyId, Pageable pageable);
    Page<Payment> findAll(Pageable pageable);
}

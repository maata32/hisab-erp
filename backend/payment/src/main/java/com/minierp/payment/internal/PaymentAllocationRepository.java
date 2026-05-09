package com.minierp.payment.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {
    List<PaymentAllocation> findByPaymentId(UUID paymentId);
}

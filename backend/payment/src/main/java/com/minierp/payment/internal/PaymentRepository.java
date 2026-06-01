package com.minierp.payment.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Page<Payment> findByPartyId(UUID partyId, Pageable pageable);
    Page<Payment> findAll(Pageable pageable);

    /**
     * Statement movements for the customer: every cash event that touches the
     * balance. A refund is itself an outgoing payment (CUSTOMER_REFUND) — an
     * inflow-to-the-customer / outflow from us — kept here so the statement total
     * reconciles with the AR balance. Both the original CUSTOMER_PAYMENT and the
     * refund payment stay CONFIRMED and visible on the timeline.
     */
    @Query("SELECT p FROM Payment p WHERE p.partyId = :partyId " +
           "AND p.status = 'CONFIRMED' " +
           "AND p.type IN ('CUSTOMER_PAYMENT','CUSTOMER_DEPOSIT','CUSTOMER_REFUND') " +
           "AND p.paymentDate >= :from AND p.paymentDate <= :to " +
           "ORDER BY p.paymentDate ASC")
    List<Payment> findConfirmedForCustomerStatement(UUID partyId, LocalDate from, LocalDate to);
}

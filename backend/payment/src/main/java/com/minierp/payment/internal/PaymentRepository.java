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
     * balance. Refunds (CUSTOMER_REFUND) are inflows-to-the-customer / outflows
     * from us — kept here so the statement total reconciles with the AR balance.
     * The original CUSTOMER_PAYMENT remains visible too, even after it has been
     * refunded (status REFUNDED), because it still tells the story of what
     * happened on the timeline.
     */
    @Query("SELECT p FROM Payment p WHERE p.partyId = :partyId " +
           "AND p.status IN ('CONFIRMED','REFUNDED') " +
           "AND p.type IN ('CUSTOMER_PAYMENT','CUSTOMER_DEPOSIT','CUSTOMER_REFUND') " +
           "AND p.paymentDate >= :from AND p.paymentDate <= :to " +
           "ORDER BY p.paymentDate ASC")
    List<Payment> findConfirmedForCustomerStatement(UUID partyId, LocalDate from, LocalDate to);
}

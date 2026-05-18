package com.minierp.customer.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface CustomerCreditRepository extends JpaRepository<CustomerCredit, UUID> {
    List<CustomerCredit> findByCustomerIdAndStatusOrderByCreatedAtAsc(UUID customerId, CustomerCreditStatus status);

    @Query("SELECT c FROM CustomerCredit c WHERE c.customerId = :customerId " +
           "AND c.status = 'ACTIVE' " +
           "AND c.createdAt >= :from AND c.createdAt <= :to " +
           "ORDER BY c.createdAt ASC")
    List<CustomerCredit> findActiveForStatement(UUID customerId, Instant from, Instant to);
}

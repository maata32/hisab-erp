package com.minierp.customer.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface CustomerCreditRepository extends JpaRepository<CustomerCredit, UUID> {
    List<CustomerCredit> findByPartyIdAndStatusOrderByCreatedAtAsc(UUID partyId, CustomerCreditStatus status);

    @Query("SELECT c FROM CustomerCredit c WHERE c.partyId = :partyId " +
           "AND c.status = 'ACTIVE' " +
           "AND c.createdAt >= :from AND c.createdAt <= :to " +
           "ORDER BY c.createdAt ASC")
    List<CustomerCredit> findActiveForStatement(UUID partyId, Instant from, Instant to);
}

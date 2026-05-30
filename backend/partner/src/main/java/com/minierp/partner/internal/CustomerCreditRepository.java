package com.minierp.partner.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface CustomerCreditRepository extends JpaRepository<CustomerCredit, UUID> {
    List<CustomerCredit> findByPartyIdAndStatusOrderByCreatedAtAsc(UUID partyId, CustomerCreditStatus status);

    List<CustomerCredit> findBySourcePaymentIdAndStatus(UUID sourcePaymentId, CustomerCreditStatus status);

    /**
     * Sum of active credits' remaining_amount grouped by party — used by the
     * partner list to surface the "we owe them" credit balance alongside the
     * AR / AP balances. Returned rows are (party_id, sum).
     */
    @Query("""
            SELECT c.partyId, COALESCE(SUM(c.remainingAmount), 0)
            FROM CustomerCredit c
            WHERE c.partyId IN :ids
              AND c.status = com.minierp.partner.internal.CustomerCreditStatus.ACTIVE
            GROUP BY c.partyId
            """)
    List<Object[]> sumActiveRemainingByPartyIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);

    @Query("SELECT c FROM CustomerCredit c WHERE c.partyId = :partyId " +
           "AND c.status = 'ACTIVE' " +
           "AND c.createdAt >= :from AND c.createdAt <= :to " +
           "ORDER BY c.createdAt ASC")
    List<CustomerCredit> findActiveForStatement(UUID partyId, Instant from, Instant to);
}

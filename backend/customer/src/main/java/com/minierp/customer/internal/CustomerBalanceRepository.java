package com.minierp.customer.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CustomerBalanceRepository extends JpaRepository<CustomerBalance, UUID> {
    Optional<CustomerBalance> findByPartyId(UUID partyId);

    List<CustomerBalance> findByPartyIdIn(Collection<UUID> partyIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM CustomerBalance b WHERE b.partyId = :partyId")
    Optional<CustomerBalance> lockByPartyId(UUID partyId);
}

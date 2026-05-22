package com.minierp.partner.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ApBalanceRepository extends JpaRepository<ApBalance, UUID> {
    Optional<ApBalance> findByPartyId(UUID partyId);

    List<ApBalance> findByPartyIdIn(Collection<UUID> partyIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ApBalance b WHERE b.partyId = :partyId")
    Optional<ApBalance> lockByPartyId(UUID partyId);
}

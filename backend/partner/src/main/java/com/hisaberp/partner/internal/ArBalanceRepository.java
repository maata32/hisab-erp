package com.hisaberp.partner.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ArBalanceRepository extends JpaRepository<ArBalance, UUID> {
    Optional<ArBalance> findByPartyId(UUID partyId);

    List<ArBalance> findByPartyIdIn(Collection<UUID> partyIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ArBalance b WHERE b.partyId = :partyId")
    Optional<ArBalance> lockByPartyId(UUID partyId);
}

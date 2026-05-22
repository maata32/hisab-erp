package com.minierp.customer.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SupplierBalanceRepository extends JpaRepository<SupplierBalance, UUID> {
    Optional<SupplierBalance> findByPartyId(UUID partyId);

    List<SupplierBalance> findByPartyIdIn(Collection<UUID> partyIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM SupplierBalance b WHERE b.partyId = :partyId")
    Optional<SupplierBalance> lockByPartyId(UUID partyId);
}

package com.minierp.treasury.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface VaultRepository extends JpaRepository<Vault, UUID> {

    @Query("SELECT v FROM Vault v WHERE v.tenantId = :tenantId")
    Optional<Vault> findByTenant(@Param("tenantId") UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vault v WHERE v.tenantId = :tenantId")
    Optional<Vault> lockByTenant(@Param("tenantId") UUID tenantId);
}

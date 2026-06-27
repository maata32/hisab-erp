package com.hisaberp.treasury.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

interface VaultMovementRepository extends JpaRepository<VaultMovement, UUID> {

    Page<VaultMovement> findByVaultIdOrderByOccurredAtDesc(UUID vaultId, Pageable pageable);

    Page<VaultMovement> findByVaultIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID vaultId, Instant from, Instant to, Pageable pageable);
}

package com.minierp.lotexpiry.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface LotMovementRepository extends JpaRepository<LotMovement, UUID> {

    List<LotMovement> findByLotIdOrderByCreatedAtDesc(UUID lotId);
}

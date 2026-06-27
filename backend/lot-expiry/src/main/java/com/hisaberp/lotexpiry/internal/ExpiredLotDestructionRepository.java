package com.hisaberp.lotexpiry.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ExpiredLotDestructionRepository extends JpaRepository<ExpiredLotDestruction, UUID> {

    List<ExpiredLotDestruction> findByLotIdOrderByDestructionDateDesc(UUID lotId);
}

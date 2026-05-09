package com.minierp.pricing.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PriceTierRepository extends JpaRepository<PriceTier, UUID> {
    Optional<PriceTier> findByCode(String code);
    boolean existsByCode(String code);
    Optional<PriceTier> findFirstByDefaultTierTrue();
    List<PriceTier> findByActiveTrue();
}

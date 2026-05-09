package com.minierp.uom.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UomRepository extends JpaRepository<Uom, UUID> {
    Optional<Uom> findByCode(String code);
    List<Uom> findByCategoryIdOrderByRatioToBaseAsc(UUID categoryId);
    boolean existsByCode(String code);
    Optional<Uom> findByCategoryIdAndIsBaseTrue(UUID categoryId);
}

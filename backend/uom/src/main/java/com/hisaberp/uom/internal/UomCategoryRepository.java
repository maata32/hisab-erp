package com.hisaberp.uom.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UomCategoryRepository extends JpaRepository<UomCategory, UUID> {
    Optional<UomCategory> findByCode(String code);
    boolean existsByCode(String code);
}

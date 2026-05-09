package com.minierp.catalog.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface BrandRepository extends JpaRepository<Brand, UUID> {
    Optional<Brand> findByCode(String code);
    boolean existsByCode(String code);
    Page<Brand> findByActiveTrue(Pageable pageable);
}

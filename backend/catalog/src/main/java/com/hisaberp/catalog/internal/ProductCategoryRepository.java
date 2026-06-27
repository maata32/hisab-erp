package com.hisaberp.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    Optional<ProductCategory> findByCode(String code);
    boolean existsByCode(String code);
    List<ProductCategory> findByActiveTrueOrderBySortOrderAsc();
    List<ProductCategory> findByParentId(UUID parentId);
}

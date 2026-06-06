package com.minierp.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    List<ProductVariant> findByProductIdOrderBySkuAsc(UUID productId);
    List<ProductVariant> findByProductId(UUID productId);
    Optional<ProductVariant> findFirstByProductIdAndDefaultVariantTrue(UUID productId);
    boolean existsBySku(String sku);
    void deleteByProductId(UUID productId);
}

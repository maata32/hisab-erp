package com.hisaberp.catalog.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    Optional<Product> findByBarcode(String barcode);

    boolean existsBySku(String sku);

    boolean existsByBarcode(String barcode);

    @Query("""
            SELECT p FROM Product p
             WHERE (:includeInactive = TRUE OR p.active = true)
               AND (:q IS NULL
                    OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR p.barcode = :q)
               AND (:categoryId IS NULL OR p.categoryId = :categoryId)
               AND (:brandId IS NULL OR p.brandId = :brandId)
            """)
    Page<Product> search(@Param("q") String q,
                         @Param("categoryId") UUID categoryId,
                         @Param("brandId") UUID brandId,
                         @Param("includeInactive") boolean includeInactive,
                         Pageable pageable);

    List<Product> findAllByIdIn(List<UUID> ids);
}

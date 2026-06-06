package com.minierp.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, UUID> {
    List<ProductAttributeValue> findByProductId(UUID productId);
    void deleteByProductId(UUID productId);
    boolean existsByAttributeValueId(UUID attributeValueId);
}

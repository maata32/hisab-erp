package com.minierp.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface VariantAttributeValueRepository extends JpaRepository<VariantAttributeValue, UUID> {
    List<VariantAttributeValue> findByVariantId(UUID variantId);
    List<VariantAttributeValue> findByVariantIdIn(Collection<UUID> variantIds);
    void deleteByVariantId(UUID variantId);
    boolean existsByAttributeValueId(UUID attributeValueId);
}

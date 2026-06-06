package com.minierp.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface AttributeValueRepository extends JpaRepository<AttributeValue, UUID> {
    List<AttributeValue> findByAttributeIdOrderBySortOrderAscValueAsc(UUID attributeId);
    List<AttributeValue> findByIdIn(Collection<UUID> ids);
    boolean existsByAttributeId(UUID attributeId);
    void deleteByAttributeId(UUID attributeId);
}

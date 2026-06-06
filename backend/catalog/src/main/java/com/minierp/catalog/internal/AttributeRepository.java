package com.minierp.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AttributeRepository extends JpaRepository<Attribute, UUID> {
    List<Attribute> findAllByOrderBySortOrderAscNameAsc();
    boolean existsByNameIgnoreCase(String name);
}

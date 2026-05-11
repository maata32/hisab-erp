package com.minierp.inventory.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface InventoryCountRepository extends JpaRepository<InventoryCount, UUID> {

    Page<InventoryCount> findByWarehouseId(UUID warehouseId, Pageable pageable);
}

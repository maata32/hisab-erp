package com.minierp.inventory.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    Page<StockMovement> findByProductIdOrderByOccurredAtDesc(UUID productId, Pageable pageable);
    Page<StockMovement> findByWarehouseIdOrderByOccurredAtDesc(UUID warehouseId, Pageable pageable);
}

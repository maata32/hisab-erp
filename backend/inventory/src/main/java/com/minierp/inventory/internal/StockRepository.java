package com.minierp.inventory.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StockRepository extends JpaRepository<Stock, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.warehouseId = :w AND s.productId = :p")
    Optional<Stock> lockByWarehouseAndProduct(@Param("w") UUID warehouseId, @Param("p") UUID productId);

    Optional<Stock> findByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

    List<Stock> findByProductId(UUID productId);

    List<Stock> findByWarehouseIdAndProductIdIn(UUID warehouseId, List<UUID> productIds);
}

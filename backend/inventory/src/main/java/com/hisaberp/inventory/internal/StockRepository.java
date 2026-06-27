package com.hisaberp.inventory.internal;

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
    @Query("SELECT s FROM Stock s WHERE s.warehouseId = :w AND s.variantId = :v")
    Optional<Stock> lockByWarehouseAndVariant(@Param("w") UUID warehouseId, @Param("v") UUID variantId);

    Optional<Stock> findByWarehouseIdAndVariantId(UUID warehouseId, UUID variantId);

    List<Stock> findByVariantId(UUID variantId);

    List<Stock> findByProductId(UUID productId);

    List<Stock> findByWarehouseId(UUID warehouseId);
}

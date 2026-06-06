package com.minierp.lotexpiry.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface ProductLotRepository extends JpaRepository<ProductLot, UUID> {

    List<ProductLot> findByProductVariantIdAndWarehouseIdAndStatusOrderByExpirationDateAsc(
            UUID productVariantId, UUID warehouseId, LotStatus status);

    Page<ProductLot> findByProductVariantId(UUID productVariantId, Pageable pageable);

    Page<ProductLot> findByProductId(UUID productId, Pageable pageable);

    @Query("SELECT pl FROM ProductLot pl WHERE pl.expirationDate <= :threshold AND pl.status = 'ACTIVE'")
    List<ProductLot> findExpiringBefore(@Param("threshold") LocalDate threshold);

    @Modifying
    @Query("UPDATE ProductLot pl SET pl.status = 'EXPIRED' " +
           "WHERE pl.expirationDate < :today AND pl.status = 'ACTIVE'")
    int markExpiredLotsBeforeDate(@Param("today") LocalDate today);

    @Query("SELECT pl FROM ProductLot pl WHERE pl.status IN ('ACTIVE','EXPIRED') " +
           "AND (:warehouseId IS NULL OR pl.warehouseId = :warehouseId) " +
           "ORDER BY pl.expirationDate ASC")
    Page<ProductLot> findForDashboard(@Param("warehouseId") UUID warehouseId, Pageable pageable);

    @Query("SELECT pl FROM ProductLot pl WHERE pl.expirationDate <= :threshold " +
           "AND pl.status = 'ACTIVE' " +
           "AND (:warehouseId IS NULL OR pl.warehouseId = :warehouseId) " +
           "ORDER BY pl.expirationDate ASC")
    Page<ProductLot> findExpiringWithin(@Param("threshold") LocalDate threshold,
                                        @Param("warehouseId") UUID warehouseId,
                                        Pageable pageable);

    @Query("SELECT pl FROM ProductLot pl WHERE pl.status = 'EXPIRED' " +
           "AND (:warehouseId IS NULL OR pl.warehouseId = :warehouseId) " +
           "ORDER BY pl.expirationDate ASC")
    Page<ProductLot> findExpired(@Param("warehouseId") UUID warehouseId, Pageable pageable);
}

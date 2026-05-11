package com.minierp.inventory.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    Page<StockTransfer> findByFromWarehouseIdOrToWarehouseId(
            UUID fromId, UUID toId, Pageable pageable);

    @Query("SELECT MAX(CAST(SUBSTRING(t.transferNumber, LENGTH(t.transferNumber) - 4) AS int)) " +
           "FROM StockTransfer t WHERE t.tenantId = :tenantId AND YEAR(t.createdAt) = :year")
    Optional<Integer> findMaxSequence(UUID tenantId, int year);
}

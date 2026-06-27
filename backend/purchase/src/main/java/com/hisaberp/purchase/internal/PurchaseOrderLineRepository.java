package com.hisaberp.purchase.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findByPurchaseOrderIdOrderByLineNumberAsc(UUID purchaseOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM PurchaseOrderLine l WHERE l.id = :id")
    Optional<PurchaseOrderLine> lockById(UUID id);
}

package com.minierp.purchase.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Page<PurchaseOrder> findByPartyId(UUID partyId, Pageable pageable);

    @Query("SELECT po FROM PurchaseOrder po WHERE " +
           "(:partyId IS NULL OR po.partyId = :partyId) AND " +
           "(:status IS NULL OR po.status = :status)")
    Page<PurchaseOrder> findFiltered(UUID partyId, PurchaseOrderStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT po FROM PurchaseOrder po WHERE po.id = :id")
    Optional<PurchaseOrder> lockById(UUID id);
}

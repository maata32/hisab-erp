package com.minierp.purchase.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, UUID> {

    Page<PurchaseInvoice> findBySupplierId(UUID supplierId, Pageable pageable);

    @Query("SELECT pi FROM PurchaseInvoice pi WHERE " +
           "(:supplierId IS NULL OR pi.supplierId = :supplierId) AND " +
           "(:status IS NULL OR pi.status = :status)")
    Page<PurchaseInvoice> findFiltered(UUID supplierId, PurchaseInvoiceStatus status, Pageable pageable);

    @Query("SELECT pi FROM PurchaseInvoice pi WHERE pi.supplierId = :supplierId " +
           "AND pi.status IN ('ISSUED','PARTIAL') ORDER BY pi.dueDate ASC NULLS LAST, pi.invoiceDate ASC")
    List<PurchaseInvoice> findUnpaidBySupplier(UUID supplierId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pi FROM PurchaseInvoice pi WHERE pi.id = :id")
    Optional<PurchaseInvoice> lockById(UUID id);
}

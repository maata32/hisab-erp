package com.minierp.purchase.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {
    Page<GoodsReceipt> findByPartyId(UUID partyId, Pageable pageable);
    Page<GoodsReceipt> findByPurchaseInvoiceId(UUID purchaseInvoiceId, Pageable pageable);
    Page<GoodsReceipt> findAll(Pageable pageable);

    /**
     * Warehouse ids of the invoice's non-cancelled INBOUND receipts, oldest first
     * — used to route a supplier return back through the warehouse goods came in.
     */
    @Query("""
            SELECT g.warehouseId FROM GoodsReceipt g
            WHERE g.purchaseInvoiceId = :invoiceId
              AND g.status <> com.minierp.purchase.internal.GoodsReceiptStatus.CANCELLED
              AND g.type = com.minierp.purchase.internal.GoodsReceiptType.INBOUND
              AND g.warehouseId IS NOT NULL
            ORDER BY g.createdAt ASC
            """)
    List<UUID> findInboundWarehouseIds(@Param("invoiceId") UUID invoiceId, Pageable pageable);
}

package com.minierp.purchase.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, UUID> {
    List<GoodsReceiptLine> findByGoodsReceiptId(UUID goodsReceiptId);

    /**
     * Inbound received quantity per variant across non-cancelled INBOUND receipts
     * of the invoice. RETURN-type receipts are excluded — they send goods back to
     * the supplier and would otherwise deflate the "received" total used by the
     * invoice reception-status logic.
     */
    @Query("""
            SELECT grl.variantId, SUM(grl.quantityReceived)
            FROM GoodsReceiptLine grl, GoodsReceipt gr
            WHERE grl.goodsReceiptId = gr.id
              AND gr.purchaseInvoiceId = :invoiceId
              AND gr.status <> com.minierp.purchase.internal.GoodsReceiptStatus.CANCELLED
              AND gr.type = com.minierp.purchase.internal.GoodsReceiptType.INBOUND
            GROUP BY grl.variantId
            """)
    List<Object[]> sumReceivedByVariantForInvoice(@Param("invoiceId") UUID invoiceId);
}

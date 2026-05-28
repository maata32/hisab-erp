package com.minierp.delivery.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface DeliveryLineRepository extends JpaRepository<DeliveryLine, UUID> {
    List<DeliveryLine> findByDeliveryId(UUID deliveryId);

    /**
     * Outbound shipped quantity per product across non-cancelled forward BLs of
     * the invoice. RETURN-type BLs are excluded — they represent goods coming
     * back, not goods leaving, and would inflate the "delivered" total used by
     * credit-note caps and invoice delivery-status logic.
     */
    @Query("""
            SELECT dl.productId, SUM(dl.quantityDelivered)
            FROM DeliveryLine dl, Delivery d
            WHERE dl.deliveryId = d.id
              AND d.invoiceId = :invoiceId
              AND d.status <> com.minierp.delivery.internal.DeliveryStatus.CANCELLED
              AND d.type = com.minierp.delivery.internal.DeliveryType.OUTBOUND
            GROUP BY dl.productId
            """)
    List<Object[]> sumDeliveredByProductForInvoice(@Param("invoiceId") UUID invoiceId);
}

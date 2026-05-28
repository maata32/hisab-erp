package com.minierp.delivery.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface DeliveryLineRepository extends JpaRepository<DeliveryLine, UUID> {
    List<DeliveryLine> findByDeliveryId(UUID deliveryId);

    /**
     * Aggregate total delivered quantity per product across all non-cancelled
     * BLs that reference the given invoice. Returns rows of
     * [product_id, sum_quantity_delivered].
     */
    @Query("""
            SELECT dl.productId, SUM(dl.quantityDelivered)
            FROM DeliveryLine dl, Delivery d
            WHERE dl.deliveryId = d.id
              AND d.invoiceId = :invoiceId
              AND d.status <> com.minierp.delivery.internal.DeliveryStatus.CANCELLED
            GROUP BY dl.productId
            """)
    List<Object[]> sumDeliveredByProductForInvoice(@Param("invoiceId") UUID invoiceId);
}

package com.minierp.sales.api;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Order mutation facade used by the delivery module to auto-derive
 * the order's delivery-side status from recorded shipments.
 */
public interface OrderOperations {

    /**
     * Recompute the order's status from total delivered quantities per product.
     * Caller passes the cumulative delivered quantities aggregated across all
     * non-cancelled deliveries for the order.
     *
     * Only transitions between {CONFIRMED, PARTIALLY_DELIVERED, DELIVERED} —
     * INVOICED/CANCELLED orders are left untouched.
     */
    void recomputeDeliveryStatus(UUID orderId, Map<UUID, BigDecimal> totalDeliveredByProduct);
}

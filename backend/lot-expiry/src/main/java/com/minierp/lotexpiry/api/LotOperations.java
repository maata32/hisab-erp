package com.minierp.lotexpiry.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public API consumed by other modules (pos, sales, delivery).
 * Keeps internal entities encapsulated.
 */
public interface LotOperations {

    /**
     * FEFO selection: pick the minimum set of lots to satisfy requestedQty,
     * oldest-expiry-first.
     */
    List<LotAllocation> selectFEFO(UUID productId, UUID warehouseId, BigDecimal requestedQty);

    /**
     * Consume a previously calculated FEFO allocation — decrement remaining qty
     * and record LotMovements. Called after a sale/delivery is confirmed.
     */
    void consumeAllocations(List<LotAllocation> allocations, String referenceType, UUID referenceId);

    /**
     * Receive stock into a new or existing lot.
     */
    UUID receiveLot(UUID productId, UUID warehouseId, UUID uomId,
                    String lotNumber, java.time.LocalDate expirationDate,
                    java.time.LocalDate productionDate,
                    BigDecimal quantity, BigDecimal unitCost,
                    UUID supplierId, UUID purchaseOrderId);

    /** Block a lot (quality hold). */
    void blockLot(UUID lotId, String reason);

    /** Check if product has lot-expiry tracking enabled. */
    boolean isTrackingExpiry(UUID productId);
}

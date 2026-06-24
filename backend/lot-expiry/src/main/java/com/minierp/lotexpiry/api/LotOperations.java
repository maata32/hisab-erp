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
    List<LotAllocation> selectFEFO(UUID variantId, UUID warehouseId, BigDecimal requestedQty);

    /**
     * Consume a previously calculated FEFO allocation — decrement remaining qty
     * and record LotMovements. Called after a sale/delivery is confirmed.
     */
    void consumeAllocations(List<LotAllocation> allocations, String referenceType, UUID referenceId);

    /**
     * Outbound hook: when the variant's product is lot/expiry tracked, consume {@code qty}
     * from its ACTIVE lots oldest-expiry-first (FEFO) and record SALE_OUT movements.
     * Tolerant — if the available lots cannot cover {@code qty} (incomplete lot data,
     * e.g. opening stock posted without a lot, or POS oversell), it consumes what exists
     * and logs a warning rather than blocking the sale/delivery (total stock stays the
     * source of truth). No-op for non-tracked products. Called right after the stock debit.
     */
    void consumeFefoIfTracked(UUID variantId, UUID warehouseId, BigDecimal qty,
                              String referenceType, UUID referenceId);

    /**
     * Return hook: restore {@code qty} back into a lot of this variant/warehouse — reverse FEFO
     * (newest-expiry surviving lot first, reviving EXHAUSTED → ACTIVE). When no lot survives, a
     * fresh return lot is created if the product is lot/expiry tracked (using {@code productId} to
     * resolve its base UoM and shelf life); otherwise it is a no-op. Records a RETURN_IN movement.
     * Called alongside the SALE_RETURN stock restock.
     */
    void restoreLotsOnReturn(UUID productId, UUID variantId, UUID warehouseId, BigDecimal qty,
                             String referenceType, UUID referenceId);

    /**
     * Manual lot selection (overrides FEFO): consume the exact {@code allocations} (lotId + quantity)
     * for the given variant/warehouse. Validates that each lot is an ACTIVE lot of this
     * variant+warehouse with enough remaining and that the allocations sum to {@code expectedQty},
     * then decrements them and records SALE_OUT movements. Throws a 422 BusinessException on any
     * validation failure (so the sale/delivery is rejected rather than silently mis-consumed).
     */
    void consumeExplicitLots(UUID variantId, UUID warehouseId, BigDecimal expectedQty,
                             List<LotAllocation> allocations, String referenceType, UUID referenceId);

    /**
     * Receive stock into a new or existing lot.
     */
    UUID receiveLot(UUID variantId, UUID warehouseId, UUID uomId,
                    String lotNumber, java.time.LocalDate expirationDate,
                    java.time.LocalDate productionDate,
                    BigDecimal quantity, BigDecimal unitCost,
                    UUID supplierId, UUID purchaseOrderId);

    /** Block a lot (quality hold). */
    void blockLot(UUID lotId, String reason);

    /** Check if product has lot-expiry tracking enabled. */
    boolean isTrackingExpiry(UUID productId);
}

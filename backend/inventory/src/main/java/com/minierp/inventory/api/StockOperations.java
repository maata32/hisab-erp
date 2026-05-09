package com.minierp.inventory.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The transactional API other modules call to mutate stock. All quantities are
 * expressed in the product's base UoM. Implementations take a pessimistic lock on
 * the affected stock row, append an immutable {@link StockMovementDto} and update
 * {@code qty_on_hand} (and {@code average_cost} for inflows).
 */
public interface StockOperations {

    /**
     * Inflow at a known unit cost — recomputes CMP:
     *   newCost = (oldQty × oldCost + inflowQty × inflowCost) / (oldQty + inflowQty)
     */
    StockMovementDto receive(UUID warehouseId, UUID productId, BigDecimal qty, BigDecimal unitCost,
                             StockMovementType type, String referenceType, UUID referenceId,
                             String referenceNumber, String note, UUID userId);

    /**
     * Outflow at the current CMP — does not change the cost.
     * Throws {@code error.inventory.insufficient_stock} if {@code qtyOnHand - qtyReserved < qty}.
     */
    StockMovementDto issue(UUID warehouseId, UUID productId, BigDecimal qty,
                           StockMovementType type, String referenceType, UUID referenceId,
                           String referenceNumber, String note, UUID userId);

    /**
     * Manual adjustment — signed quantity, free-form unit cost (used for inventory counts).
     * Resets the average cost to {@code unitCost} when re-establishing an opening balance.
     */
    StockMovementDto adjust(UUID warehouseId, UUID productId, BigDecimal qtySigned,
                            BigDecimal unitCost, StockMovementType type,
                            String note, UUID userId);

    StockDto getStock(UUID warehouseId, UUID productId);

    /**
     * Outflow that permits the stock to go negative — used by the POS module for offline-synced
     * sales where the spec mandates acceptance even when on-hand is zero. Logs a warning when
     * stock goes negative. Does NOT throw {@code error.inventory.insufficient_stock}.
     */
    StockMovementDto issueAllowNegative(UUID warehouseId, UUID productId, BigDecimal qty,
                                        StockMovementType type, String referenceType, UUID referenceId,
                                        String referenceNumber, String note, UUID userId);
}

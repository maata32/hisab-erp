package com.hisaberp.inventory.api;

import com.hisaberp.shared.util.PageResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The transactional API other modules call to mutate stock. Stock is kept per
 * (warehouse, variant) — the variant is the real stock-keeping unit. All quantities are
 * expressed in the product's base UoM. Implementations take a pessimistic lock on
 * the affected stock row, append an immutable {@link StockMovementDto} and update
 * {@code qty_on_hand} (and {@code average_cost} for inflows). The parent product is
 * resolved from the variant and stored denormalized on stock rows and movements.
 */
public interface StockOperations {

    /**
     * Inflow at a known unit cost — recomputes CMP:
     *   newCost = (oldQty × oldCost + inflowQty × inflowCost) / (oldQty + inflowQty)
     */
    StockMovementDto receive(UUID warehouseId, UUID variantId, BigDecimal qty, BigDecimal unitCost,
                             StockMovementType type, String referenceType, UUID referenceId,
                             String referenceNumber, String note, UUID userId);

    /**
     * Outflow at the current CMP — does not change the cost.
     * Throws {@code error.inventory.insufficient_stock} if {@code qtyOnHand - qtyReserved < qty}.
     */
    StockMovementDto issue(UUID warehouseId, UUID variantId, BigDecimal qty,
                           StockMovementType type, String referenceType, UUID referenceId,
                           String referenceNumber, String note, UUID userId);

    /**
     * Manual adjustment — signed quantity, free-form unit cost (used for inventory counts).
     * Resets the average cost to {@code unitCost} when re-establishing an opening balance.
     */
    StockMovementDto adjust(UUID warehouseId, UUID variantId, BigDecimal qtySigned,
                            BigDecimal unitCost, StockMovementType type,
                            String note, UUID userId);

    StockDto getStock(UUID warehouseId, UUID variantId);

    java.util.List<StockDto> listByWarehouse(UUID warehouseId);

    /**
     * Returns, for every product that has at least one stock row, the available
     * quantity per active warehouse (summed across its variants). Warehouses are listed
     * in a stable order: the default warehouse first, then others sorted by code. Missing
     * rows are reported as zero so callers can rely on a uniform warehouse vector.
     */
    java.util.List<ProductStockBreakdownDto> listStockBreakdownByProduct();

    /**
     * Outflow that permits the stock to go negative — used by the POS module for offline-synced
     * sales where the spec mandates acceptance even when on-hand is zero. Logs a warning when
     * stock goes negative. Does NOT throw {@code error.inventory.insufficient_stock}.
     */
    StockMovementDto issueAllowNegative(UUID warehouseId, UUID variantId, BigDecimal qty,
                                        StockMovementType type, String referenceType, UUID referenceId,
                                        String referenceNumber, String note, UUID userId);

    /**
     * Append-only history of stock movements for a variant, optionally scoped to a single
     * warehouse. Ordered by occurredAt DESC.
     */
    PageResponse<StockMovementDto> listMovements(UUID variantId, UUID warehouseId, Pageable pageable);

    /** ID of the tenant's default warehouse, if one exists. */
    java.util.Optional<UUID> findDefaultWarehouseId();
}

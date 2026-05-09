package com.minierp.inventory.api;

/**
 * Reasons a stock row changed. New types should not break existing analytic queries
 * — append, never reorder.
 */
public enum StockMovementType {
    PURCHASE_RECEIPT,
    SALE,
    SALE_RETURN,
    ADJUSTMENT,
    TRANSFER_OUT,
    TRANSFER_IN,
    INVENTORY_COUNT,
    EXPIRY_DESTRUCTION,
    OPENING_BALANCE
}

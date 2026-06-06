package com.minierp.inventory.api;

import java.math.BigDecimal;
import java.util.UUID;

public record StockDto(
        UUID id,
        UUID warehouseId,
        UUID variantId,
        UUID productId,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved,
        BigDecimal qtyAvailable,
        BigDecimal averageCost) {}

package com.hisaberp.inventory.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductStockBreakdownDto(
        UUID productId,
        List<WarehouseStockEntry> warehouses) {

    public record WarehouseStockEntry(
            UUID warehouseId,
            String warehouseCode,
            String warehouseName,
            boolean isDefault,
            BigDecimal qtyAvailable) {}
}

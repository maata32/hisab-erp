package com.minierp.inventory.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class InventoryCountDto {

    public record CountResponse(
            UUID id,
            String countNumber,
            UUID warehouseId,
            String status,
            LocalDate countDate,
            Instant validatedAt,
            UUID validatedBy,
            String notes,
            List<LineResponse> lines) {}

    public record LineResponse(
            UUID id,
            UUID productId,
            UUID lotId,
            UUID uomId,
            BigDecimal theoreticalQty,
            BigDecimal countedQty,
            BigDecimal discrepancy,
            BigDecimal unitCost,
            String notes) {}
}

package com.minierp.lotexpiry.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class LotDto {

    public record LotResponse(
            UUID id,
            UUID productId,
            UUID warehouseId,
            String lotNumber,
            LocalDate productionDate,
            LocalDate expirationDate,
            BigDecimal initialQuantity,
            BigDecimal quantityRemaining,
            UUID uomId,
            String status,
            String blockedReason,
            String notes,
            long daysUntilExpiry) {}

    public record CreateLotRequest(
            UUID productId,
            UUID warehouseId,
            UUID uomId,
            String lotNumber,
            LocalDate expirationDate,
            LocalDate productionDate,
            BigDecimal quantity,
            BigDecimal unitCost,
            UUID supplierId,
            String notes) {}

    public record DestroyLotRequest(
            BigDecimal quantity,
            String method,
            BigDecimal cost,
            String notes) {}

    public record AlertConfigRequest(
            int daysBeforeExpiry,
            String severity,
            String notifyRoles,
            boolean enabled) {}

    public record AlertConfigResponse(
            UUID id,
            int daysBeforeExpiry,
            String severity,
            String notifyRoles,
            boolean enabled) {}
}

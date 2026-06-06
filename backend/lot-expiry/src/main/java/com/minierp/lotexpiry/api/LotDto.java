package com.minierp.lotexpiry.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class LotDto {

    public record LotResponse(
            UUID id,
            UUID productId,
            UUID variantId,
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
            UUID variantId,
            UUID warehouseId,
            UUID uomId,
            String lotNumber,
            LocalDate expirationDate,
            LocalDate productionDate,
            BigDecimal quantity,
            BigDecimal unitCost,
            UUID supplierId,
            String notes) {}

    /** Opening stock for an expiry-tracked product: posts stock AND creates the lot. */
    public record OpeningBalanceRequest(
            @NotNull UUID warehouseId,
            @NotNull UUID variantId,
            @NotNull @DecimalMin("0.000001") BigDecimal quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitCost,
            @NotBlank String lotNumber,
            @NotNull LocalDate expirationDate,
            LocalDate productionDate,
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

    /** CDC §15.4 POST /lots/select-fefo body. */
    public record SelectFefoRequest(
            UUID variantId,
            UUID warehouseId,
            UUID uomId,
            BigDecimal quantity) {}
}

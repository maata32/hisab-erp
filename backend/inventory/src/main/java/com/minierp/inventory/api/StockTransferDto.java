package com.minierp.inventory.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class StockTransferDto {

    public record TransferResponse(
            UUID id,
            String transferNumber,
            UUID fromWarehouseId,
            UUID toWarehouseId,
            String status,
            LocalDate scheduledDate,
            Instant completedAt,
            String notes,
            List<LineResponse> lines) {}

    public record LineResponse(
            UUID id,
            UUID productId,
            UUID lotId,
            UUID uomId,
            BigDecimal quantityRequested,
            BigDecimal quantityTransferred,
            String notes) {}
}

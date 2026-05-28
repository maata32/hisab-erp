package com.minierp.delivery.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class DeliveryDto {

    public record LineDto(
            UUID id,
            UUID productId,
            UUID uomId,
            BigDecimal quantityOrdered,
            BigDecimal quantityDelivered,
            String status,
            String productName,
            String sku
    ) {}

    public record DeliveryResponse(
            UUID id,
            String number,
            UUID customerId,
            String customerName,
            UUID invoiceId,
            UUID warehouseId,
            String status,
            LocalDate scheduledDate,
            Instant deliveredAt,
            String address,
            String contactPhone,
            String signedBy,
            String notes,
            List<LineDto> lines,
            Instant createdAt
    ) {}

    public record LineRequest(
            UUID productId,
            UUID uomId,
            BigDecimal quantityOrdered,
            String productName,
            String sku
    ) {}

    public record CreateDeliveryRequest(
            UUID customerId,
            UUID invoiceId,
            UUID warehouseId,
            LocalDate scheduledDate,
            String address,
            String contactPhone,
            String notes,
            List<LineRequest> lines
    ) {}

    public record UpdateStatusRequest(
            String status,
            String signedBy
    ) {}

    public record RecordDeliveryRequest(
            List<LineDelivered> lines,
            String signedBy,
            String notes
    ) {}

    public record LineDelivered(
            UUID lineId,
            BigDecimal quantityDelivered
    ) {}
}

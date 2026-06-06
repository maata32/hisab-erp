package com.minierp.pos.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateSaleRequest(
        @NotBlank @Size(max = 64) String idempotencyKey,
        @NotNull UUID registerId,
        @NotNull UUID sessionId,
        UUID customerId,
        UUID priceTierId,
        Instant occurredAt,
        String note,
        @NotEmpty @Valid List<SaleLineRequest> lines,
        @Valid PaymentRequest payment) {

    public record SaleLineRequest(
            @NotNull UUID variantId,
            UUID uomId,
            @NotNull @DecimalMin("0.000001") BigDecimal quantity,
            @DecimalMin("0.0000") BigDecimal unitDiscount) {}

    public record PaymentRequest(
            @DecimalMin("0.00") BigDecimal cash,
            @DecimalMin("0.00") BigDecimal card,
            @DecimalMin("0.00") BigDecimal mobile,
            @DecimalMin("0.00") BigDecimal credit) {}
}

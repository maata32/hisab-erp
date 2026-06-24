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
            @DecimalMin("0.0000") BigDecimal unitDiscount,
            /** Optional manual lot selection (overrides FEFO). When set, the sum of the allocation
             *  quantities must equal the line's base quantity and each lot must be a valid ACTIVE lot
             *  of this variant/warehouse with enough remaining. */
            @Valid List<LotAllocationRequest> lotAllocations) {

        /** Backwards-compatible constructor (no manual lot selection → automatic FEFO). */
        public SaleLineRequest(UUID variantId, UUID uomId, BigDecimal quantity, BigDecimal unitDiscount) {
            this(variantId, uomId, quantity, unitDiscount, null);
        }
    }

    public record LotAllocationRequest(
            @NotNull UUID lotId,
            @NotNull @DecimalMin("0.000001") BigDecimal quantity) {}

    public record PaymentRequest(
            @DecimalMin("0.00") BigDecimal cash,
            @DecimalMin("0.00") BigDecimal card,
            @DecimalMin("0.00") BigDecimal mobile,
            @DecimalMin("0.00") BigDecimal credit) {}
}

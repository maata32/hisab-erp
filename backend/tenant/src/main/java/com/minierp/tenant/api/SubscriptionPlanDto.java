package com.minierp.tenant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Admin view of a subscription plan (super-admin CRUD). Null limits mean "unlimited". */
public record SubscriptionPlanDto(
        UUID id,
        String code,
        String name,
        BigDecimal monthlyPrice,
        BigDecimal annualPrice,
        Integer maxCashRegisters,
        Integer maxUsers,
        Integer maxProducts,
        Integer maxProductImages,
        boolean active
) {

    public record CreateRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull @PositiveOrZero BigDecimal monthlyPrice,
            @PositiveOrZero BigDecimal annualPrice,
            @PositiveOrZero Integer maxCashRegisters,
            @PositiveOrZero Integer maxUsers,
            @PositiveOrZero Integer maxProducts,
            @PositiveOrZero Integer maxProductImages
    ) {}

    public record UpdateRequest(
            @Size(max = 100) String name,
            @PositiveOrZero BigDecimal monthlyPrice,
            @PositiveOrZero BigDecimal annualPrice,
            @PositiveOrZero Integer maxCashRegisters,
            @PositiveOrZero Integer maxUsers,
            @PositiveOrZero Integer maxProducts,
            @PositiveOrZero Integer maxProductImages,
            Boolean active
    ) {}
}

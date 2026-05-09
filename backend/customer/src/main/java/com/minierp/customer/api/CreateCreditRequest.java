package com.minierp.customer.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateCreditRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull String source,
        String notes
) {}

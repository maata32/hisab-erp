package com.minierp.partner.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ActivateCustomerRoleRequest(
        @NotBlank @Size(max = 50) String customerCode,
        UUID defaultPriceTierId,
        BigDecimal customerCreditLimit) {}

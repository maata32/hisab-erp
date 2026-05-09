package com.minierp.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 50) String code,
        String type,
        @NotBlank @Size(max = 250) String name,
        @Size(max = 150) String email,
        @Size(max = 30) String phone,
        @Size(max = 500) String address,
        BigDecimal creditLimit,
        String currency,
        @Size(max = 500) String notes
) {}

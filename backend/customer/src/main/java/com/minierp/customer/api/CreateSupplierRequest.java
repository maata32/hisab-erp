package com.minierp.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateSupplierRequest(
        @NotBlank @Size(max = 50) String code,
        String type,
        @NotBlank @Size(max = 250) String name,
        @Size(max = 150) String email,
        @Size(max = 30) String phone,
        @Size(max = 500) String address,
        @Size(max = 50) String taxId,
        @Size(max = 100) String paymentTerms,
        @Size(max = 3) String currency,
        @Size(max = 500) String notes,
        BigDecimal creditLimit) {}

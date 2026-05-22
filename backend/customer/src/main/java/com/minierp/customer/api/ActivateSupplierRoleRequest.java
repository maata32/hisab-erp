package com.minierp.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ActivateSupplierRoleRequest(
        @NotBlank @Size(max = 50) String supplierCode,
        @Size(max = 50) String taxId,
        @Size(max = 100) String paymentTerms,
        BigDecimal supplierCreditLimit) {}

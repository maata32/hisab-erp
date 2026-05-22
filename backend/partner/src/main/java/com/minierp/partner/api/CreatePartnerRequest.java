package com.minierp.partner.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Create-partner payload. At least one of {@code isCustomer} / {@code isSupplier}
 * must be true; the corresponding *Code field must be provided when the role
 * flag is set.
 */
public record CreatePartnerRequest(
        boolean isCustomer,
        boolean isSupplier,
        @Size(max = 50) String customerCode,
        @Size(max = 50) String supplierCode,
        String type,
        @NotBlank @Size(max = 250) String name,
        @Size(max = 150) String email,
        @Size(max = 30) String phone,
        @Size(max = 500) String address,
        @Size(max = 50) String taxId,
        @Size(max = 100) String paymentTerms,
        @Size(max = 3) String currency,
        @Size(max = 500) String notes,
        UUID defaultPriceTierId,
        String notificationPreferences,
        BigDecimal customerCreditLimit,
        BigDecimal supplierCreditLimit) {}

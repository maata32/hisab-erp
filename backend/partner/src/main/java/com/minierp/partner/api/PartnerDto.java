package com.minierp.partner.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PartnerDto(
        UUID id,
        String code,
        String type,
        String name,
        String email,
        String phone,
        String address,
        String taxId,
        String paymentTerms,
        String currency,
        String notes,
        UUID defaultPriceTierId,
        String notificationPreferences,
        BigDecimal customerCreditLimit,
        BigDecimal supplierCreditLimit,
        boolean isCustomer,
        boolean isSupplier,
        boolean active,
        Instant createdAt,
        BigDecimal customerBalance,
        BigDecimal supplierBalance
) {}

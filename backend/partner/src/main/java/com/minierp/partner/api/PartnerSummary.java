package com.minierp.partner.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only view of a Partner record. Role-specific fields (customerCode,
 * supplierCode, credit limits, paymentTerms, defaultPriceTierId,
 * notificationPreferences) are non-null only when the corresponding role
 * flag is true.
 */
public record PartnerSummary(
        UUID id,
        String customerCode,
        String supplierCode,
        String type,
        String name,
        String phone,
        String email,
        String address,
        String currency,
        BigDecimal customerCreditLimit,
        BigDecimal supplierCreditLimit,
        UUID defaultPriceTierId,
        String paymentTerms,
        String taxId,
        String notificationPreferences,
        boolean isCustomer,
        boolean isSupplier,
        boolean active
) {}

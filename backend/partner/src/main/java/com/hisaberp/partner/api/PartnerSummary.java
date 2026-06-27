package com.hisaberp.partner.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only view of a Partner record. The {@code code} is the partner's
 * unique reference (Odoo-style {@code res.partner.ref}) — one value
 * regardless of role. Role-specific fields (credit limits, paymentTerms,
 * defaultPriceTierId, notificationPreferences) are populated only when
 * the corresponding role flag is true.
 */
public record PartnerSummary(
        UUID id,
        String code,
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

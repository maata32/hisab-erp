package com.hisaberp.partner.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Activates the customer role on an existing partner. No code field —
 * the partner already has its unique reference. Customer-specific
 * fields can be set in the same payload.
 */
public record ActivateCustomerRoleRequest(
        UUID defaultPriceTierId,
        BigDecimal customerCreditLimit) {}

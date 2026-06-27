package com.hisaberp.partner.api;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Activates the supplier role on an existing partner. No code field —
 * the partner already has its unique reference. Supplier-specific
 * fields can be set in the same payload.
 */
public record ActivateSupplierRoleRequest(
        @Size(max = 50) String taxId,
        @Size(max = 100) String paymentTerms,
        BigDecimal supplierCreditLimit) {}

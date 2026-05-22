package com.minierp.customer.api;

import java.util.UUID;

public record SupplierSummary(
        UUID id,
        String code,
        String name,
        String phone,
        String email,
        String currency,
        String paymentTerms,
        boolean active
) {}

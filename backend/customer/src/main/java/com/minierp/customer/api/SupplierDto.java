package com.minierp.customer.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SupplierDto(
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
        BigDecimal creditLimit,
        boolean active,
        Instant createdAt,
        BigDecimal balance
) {}

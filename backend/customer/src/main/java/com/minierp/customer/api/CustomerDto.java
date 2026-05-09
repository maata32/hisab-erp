package com.minierp.customer.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerDto(
        UUID id,
        String code,
        String type,
        String name,
        String email,
        String phone,
        String address,
        BigDecimal creditLimit,
        String currency,
        String notes,
        boolean active,
        Instant createdAt
) {}

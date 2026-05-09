package com.minierp.customer.api;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerSummary(
        UUID id,
        String code,
        String name,
        String phone,
        String email,
        String currency,
        BigDecimal creditLimit,
        boolean active
) {}

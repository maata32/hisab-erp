package com.minierp.uom.api;

import java.math.BigDecimal;
import java.util.UUID;

public record UomDto(
        UUID id,
        UUID categoryId,
        String categoryCode,
        String code,
        String name,
        BigDecimal ratioToBase,
        boolean isBase,
        int decimalPlaces
) {}

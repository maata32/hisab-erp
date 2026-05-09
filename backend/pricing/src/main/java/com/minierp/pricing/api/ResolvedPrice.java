package com.minierp.pricing.api;

import java.math.BigDecimal;
import java.util.UUID;

public record ResolvedPrice(
        UUID productId,
        UUID uomId,
        UUID priceTierId,
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal total,
        String currency,
        boolean taxInclusive) {}
